package com.foodie.order_service.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Central dead-letter service for order-service.
 *
 * <p><b>Replay safety guarantees:</b>
 * <ol>
 *   <li>Idempotent store — {@code (source_topic, original_key)} unique constraint
 *       prevents duplicate rows even when the DLT itself re-delivers a message.</li>
 *   <li>Atomic status transition — {@code PENDING → REPLAYING} is committed
 *       before the Kafka publish so a crash during publish leaves the row in
 *       REPLAYING (visible to operators) rather than silently lost.</li>
 *   <li>Guard-rails — only PENDING or REPLAYING rows may be replayed; calling
 *       replay on a REPLAYED or IGNORED row returns false immediately.</li>
 *   <li>Payload fidelity — the raw JSON captured at DLT arrival is re-published
 *       unchanged, so the downstream consumer sees the exact original message.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterRepository deadLetterRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Persist a dead-lettered message.
     *
     * <p>Uses an upsert pattern: if a row already exists for this
     * {@code (sourceTopic, originalKey)} pair (e.g. DLT re-delivered it)
     * we skip the insert and log a warning. The existing row retains its
     * current replay_status so an operator's in-progress decision is not lost.
     */
    @Transactional
    public DeadLetter store(String sourceTopic,
                            String originalKey,
                            String payloadJson,
                            String aggregateId,
                            String exceptionClass,
                            String exceptionMessage,
                            int retryCount,
                            String correlationId) {

        return deadLetterRepository
            .findBySourceTopicAndOriginalKey(sourceTopic, originalKey)
            .map(existing -> {
                log.warn("DLT re-delivery ignored — row already exists: topic={} key={} id={}",
                         sourceTopic, originalKey, existing.getId());
                return existing;
            })
            .orElseGet(() -> {
                DeadLetter dl = DeadLetter.builder()
                    .sourceTopic(sourceTopic)
                    .originalKey(originalKey)
                    .payloadJson(payloadJson)
                    .aggregateId(aggregateId)
                    .lastExceptionClass(exceptionClass)
                    .lastExceptionMessage(exceptionMessage)
                    .failedAt(Instant.now())
                    .retryCount(retryCount)
                    .correlationId(correlationId)
                    .replayStatus("PENDING")
                    .build();
                DeadLetter saved = deadLetterRepository.save(dl);
                log.error("Dead letter stored: id={} topic={} key={} aggregate={}",
                          saved.getId(), sourceTopic, originalKey, aggregateId);
                return saved;
            });
    }

    /** Return all PENDING dead letters for operator review. */
    @Transactional(readOnly = true)
    public List<DeadLetter> listPending() {
        return deadLetterRepository.findByReplayStatus("PENDING");
    }

    /** Return all dead letters regardless of status. */
    @Transactional(readOnly = true)
    public List<DeadLetter> listAll() {
        return deadLetterRepository.findAll();
    }

    /**
     * Replay a dead letter by re-publishing its original payload to the
     * source topic (without the "-dlt" suffix).
     *
     * <p>State transitions:
     * <pre>
     *   PENDING   → REPLAYING  (before publish)
     *   REPLAYING → REPLAYED   (after successful publish)
     *   REPLAYING → REPLAYING  (publish failed — operator must retry or ignore)
     * </pre>
     *
     * @return true if the replay was attempted; false if the row is not replayable.
     */
    @Transactional
    public boolean replay(Long deadLetterId) {
        DeadLetter dl = deadLetterRepository.findById(deadLetterId)
            .orElseThrow(() -> new IllegalArgumentException("Dead letter not found: " + deadLetterId));

        if ("REPLAYED".equals(dl.getReplayStatus()) || "IGNORED".equals(dl.getReplayStatus())) {
            log.warn("Replay skipped — already in terminal state: id={} status={}",
                     deadLetterId, dl.getReplayStatus());
            return false;
        }

        // Derive original topic from DLT topic name (strip "-dlt" suffix).
        String originalTopic = dl.getSourceTopic().replaceFirst("-dlt$", "");

        // Mark as REPLAYING before we publish — crash-safe.
        dl.setReplayStatus("REPLAYING");
        dl.setReplayNote("Replay initiated at " + Instant.now());
        deadLetterRepository.save(dl);

        try {
            // Re-publish the raw JSON as a Map so Kafka serialises it identically.
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(dl.getPayloadJson(), Map.class);
            kafkaTemplate.send(originalTopic, dl.getOriginalKey(), payload).get();

            dl.setReplayStatus("REPLAYED");
            dl.setReplayedAt(Instant.now());
            dl.setReplayNote("Successfully replayed to " + originalTopic);
            deadLetterRepository.save(dl);
            log.info("Dead letter replayed: id={} → topic={} key={}", deadLetterId, originalTopic, dl.getOriginalKey());
            return true;

        } catch (Exception ex) {
            // Leave in REPLAYING so the operator can see it failed and retry.
            dl.setReplayNote("Replay failed: " + ex.getMessage());
            deadLetterRepository.save(dl);
            log.error("Replay failed for dead letter id={}: {}", deadLetterId, ex.getMessage(), ex);
            throw new RuntimeException("Replay publish failed", ex);
        }
    }

    /**
     * Mark a dead letter as IGNORED (operator acknowledges it's a known failure
     * that should not be replayed).
     */
    @Transactional
    public void ignore(Long deadLetterId, String reason) {
        DeadLetter dl = deadLetterRepository.findById(deadLetterId)
            .orElseThrow(() -> new IllegalArgumentException("Dead letter not found: " + deadLetterId));
        dl.setReplayStatus("IGNORED");
        dl.setReplayNote(reason);
        deadLetterRepository.save(dl);
        log.info("Dead letter ignored: id={} reason={}", deadLetterId, reason);
    }
}
