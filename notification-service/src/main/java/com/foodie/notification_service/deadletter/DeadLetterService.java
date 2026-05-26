package com.foodie.notification_service.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dead-letter store and replay for notification-service using Redis.
 *
 * <p><b>Storage layout:</b>
 * <pre>
 *   notif:dl:{id}        — JSON hash of DeadLetterEntry
 *   notif:dl:all         — Redis Set of all dead-letter IDs (for listing)
 *   notif:dl:pending     — Redis Set of PENDING IDs
 * </pre>
 *
 * <p><b>Replay safety:</b>
 * Replaying re-publishes to the original topic (DLT suffix stripped).
 * The existing Redis SETNX idempotency guard in {@code IdempotencyService}
 * suppresses duplicate WebSocket pushes within 24 h.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private static final String KEY_PREFIX    = "notif:dl:";
    private static final String SET_ALL       = "notif:dl:all";
    private static final String SET_PENDING   = "notif:dl:pending";
    private static final Duration DL_TTL      = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DeadLetterEntry store(String sourceTopic, String originalKey, String payloadJson,
                                 String aggregateId, String exceptionClass, String exceptionMessage,
                                 int retryCount, String correlationId) {
        // Check for duplicate by scanning existing entries (small set in practice)
        Set<String> allIds = redisTemplate.opsForSet().members(SET_ALL);
        if (allIds != null) {
            for (String existingId : allIds) {
                String json = redisTemplate.opsForValue().get(KEY_PREFIX + existingId);
                if (json != null) {
                    try {
                        DeadLetterEntry existing = objectMapper.readValue(json, DeadLetterEntry.class);
                        if (sourceTopic.equals(existing.getSourceTopic()) && originalKey.equals(existing.getOriginalKey())) {
                            log.warn("DLT re-delivery ignored — entry exists: id={}", existingId);
                            return existing;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        String id = UUID.randomUUID().toString();
        DeadLetterEntry entry = DeadLetterEntry.builder()
            .id(id).sourceTopic(sourceTopic).originalKey(originalKey).payloadJson(payloadJson)
            .aggregateId(aggregateId).lastExceptionClass(exceptionClass)
            .lastExceptionMessage(exceptionMessage).failedAt(Instant.now())
            .retryCount(retryCount).correlationId(correlationId).replayStatus("PENDING").build();

        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(KEY_PREFIX + id, json, DL_TTL);
            redisTemplate.opsForSet().add(SET_ALL, id);
            redisTemplate.opsForSet().add(SET_PENDING, id);
            log.error("Notification dead letter stored: id={} topic={} aggregate={}", id, sourceTopic, aggregateId);
        } catch (Exception ex) {
            log.error("Failed to store notification dead letter: {}", ex.getMessage(), ex);
        }
        return entry;
    }

    public List<DeadLetterEntry> listPending() {
        return listByStatus("PENDING");
    }

    public List<DeadLetterEntry> listAll() {
        Set<String> ids = redisTemplate.opsForSet().members(SET_ALL);
        if (ids == null) return List.of();
        return ids.stream()
            .map(id -> {
                String json = redisTemplate.opsForValue().get(KEY_PREFIX + id);
                if (json == null) return null;
                try { return objectMapper.readValue(json, DeadLetterEntry.class); }
                catch (Exception ex) { return null; }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private List<DeadLetterEntry> listByStatus(String status) {
        return listAll().stream()
            .filter(e -> status.equals(e.getReplayStatus()))
            .collect(Collectors.toList());
    }

    public boolean replay(String id) {
        DeadLetterEntry entry = get(id);
        if (entry == null) throw new IllegalArgumentException("Dead letter not found: " + id);
        if ("REPLAYED".equals(entry.getReplayStatus()) || "IGNORED".equals(entry.getReplayStatus())) {
            log.warn("Replay skipped — terminal: id={} status={}", id, entry.getReplayStatus());
            return false;
        }
        String originalTopic = entry.getSourceTopic().replaceFirst("-dlt$", "");
        updateStatus(entry, "REPLAYING", "Replay initiated at " + Instant.now(), null);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(entry.getPayloadJson(), Map.class);
            kafkaTemplate.send(originalTopic, entry.getOriginalKey(), payload).get();
            updateStatus(entry, "REPLAYED", "Replayed to " + originalTopic, Instant.now());
            log.info("Notification dead letter replayed: id={} → topic={}", id, originalTopic);
            return true;
        } catch (Exception ex) {
            updateStatus(entry, "REPLAYING", "Replay failed: " + ex.getMessage(), null);
            log.error("Replay failed id={}: {}", id, ex.getMessage(), ex);
            throw new RuntimeException("Replay publish failed", ex);
        }
    }

    public void ignore(String id, String reason) {
        DeadLetterEntry entry = get(id);
        if (entry == null) throw new IllegalArgumentException("Dead letter not found: " + id);
        updateStatus(entry, "IGNORED", reason, null);
        log.info("Notification dead letter ignored: id={}", id);
    }

    private DeadLetterEntry get(String id) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + id);
        if (json == null) return null;
        try { return objectMapper.readValue(json, DeadLetterEntry.class); }
        catch (Exception ex) { return null; }
    }

    private void updateStatus(DeadLetterEntry entry, String status, String note, Instant replayedAt) {
        entry.setReplayStatus(status);
        entry.setReplayNote(note);
        if (replayedAt != null) entry.setReplayedAt(replayedAt);
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + entry.getId(), objectMapper.writeValueAsString(entry), DL_TTL);
            if ("PENDING".equals(status)) {
                redisTemplate.opsForSet().add(SET_PENDING, entry.getId());
            } else {
                redisTemplate.opsForSet().remove(SET_PENDING, entry.getId());
            }
        } catch (Exception ex) {
            log.error("Failed to update dead letter status: {}", ex.getMessage());
        }
    }
}
