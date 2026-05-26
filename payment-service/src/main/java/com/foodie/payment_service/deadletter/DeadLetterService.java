package com.foodie.payment_service.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Dead-letter store and replay for payment-service. See order-service DeadLetterService for full javadoc. */
@Slf4j @Service @RequiredArgsConstructor
public class DeadLetterService {

    private final DeadLetterRepository deadLetterRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public DeadLetter store(String sourceTopic, String originalKey, String payloadJson,
                            String aggregateId, String exceptionClass, String exceptionMessage,
                            int retryCount, String correlationId) {
        return deadLetterRepository
            .findBySourceTopicAndOriginalKey(sourceTopic, originalKey)
            .map(existing -> {
                log.warn("DLT re-delivery ignored — row exists: topic={} key={} id={}", sourceTopic, originalKey, existing.getId());
                return existing;
            })
            .orElseGet(() -> {
                DeadLetter dl = DeadLetter.builder()
                    .sourceTopic(sourceTopic).originalKey(originalKey).payloadJson(payloadJson)
                    .aggregateId(aggregateId).lastExceptionClass(exceptionClass)
                    .lastExceptionMessage(exceptionMessage).failedAt(Instant.now())
                    .retryCount(retryCount).correlationId(correlationId).replayStatus("PENDING").build();
                DeadLetter saved = deadLetterRepository.save(dl);
                log.error("Dead letter stored: id={} topic={} key={} aggregate={}", saved.getId(), sourceTopic, originalKey, aggregateId);
                return saved;
            });
    }

    @Transactional(readOnly = true)
    public List<DeadLetter> listPending() { return deadLetterRepository.findByReplayStatus("PENDING"); }

    @Transactional(readOnly = true)
    public List<DeadLetter> listAll() { return deadLetterRepository.findAll(); }

    @Transactional
    public boolean replay(Long id) {
        DeadLetter dl = deadLetterRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Dead letter not found: " + id));
        if ("REPLAYED".equals(dl.getReplayStatus()) || "IGNORED".equals(dl.getReplayStatus())) {
            log.warn("Replay skipped — terminal state: id={} status={}", id, dl.getReplayStatus());
            return false;
        }
        String originalTopic = dl.getSourceTopic().replaceFirst("-dlt$", "");
        dl.setReplayStatus("REPLAYING");
        dl.setReplayNote("Replay initiated at " + Instant.now());
        deadLetterRepository.save(dl);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(dl.getPayloadJson(), Map.class);
            kafkaTemplate.send(originalTopic, dl.getOriginalKey(), payload).get();
            dl.setReplayStatus("REPLAYED");
            dl.setReplayedAt(Instant.now());
            dl.setReplayNote("Replayed to " + originalTopic);
            deadLetterRepository.save(dl);
            log.info("Dead letter replayed: id={} → topic={}", id, originalTopic);
            return true;
        } catch (Exception ex) {
            dl.setReplayNote("Replay failed: " + ex.getMessage());
            deadLetterRepository.save(dl);
            log.error("Replay failed id={}: {}", id, ex.getMessage(), ex);
            throw new RuntimeException("Replay publish failed", ex);
        }
    }

    @Transactional
    public void ignore(Long id, String reason) {
        DeadLetter dl = deadLetterRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Dead letter not found: " + id));
        dl.setReplayStatus("IGNORED");
        dl.setReplayNote(reason);
        deadLetterRepository.save(dl);
        log.info("Dead letter ignored: id={} reason={}", id, reason);
    }
}
