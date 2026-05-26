package com.foodie.payment_service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Outbox Poller — order-service.
 *
 * <p>Runs every 5 seconds, picks up PENDING outbox rows, and publishes them
 * to Kafka. This is the relay half of the Outbox Pattern.
 *
 * <h3>Why this approach is safe</h3>
 * <ul>
 *   <li>Events are written to the DB in the same transaction as the business
 *       change (by {@link OutboxEventService}) — no dual-write window.</li>
 *   <li>This poller provides at-least-once delivery. Downstream consumers
 *       are idempotent (processed_events table) to handle any duplicates.</li>
 *   <li>If the service restarts between polling cycles, the PENDING rows
 *       remain in the DB and are picked up on the next start.</li>
 *   <li>After {@code MAX_RETRIES} failures the row is marked FAILED and an
 *       error is logged — use your alerting stack to page on FAILED rows.</li>
 * </ul>
 *
 * <h3>Production considerations</h3>
 * <ul>
 *   <li>With multiple replicas, use ShedLock or database-level advisory locks
 *       to prevent multiple pods from polling the same rows simultaneously.</li>
 *   <li>Tune {@code BATCH_SIZE} and the cron interval based on event throughput.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int BATCH_SIZE  = 100;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Poll every 5 seconds. Adjust via
     * {@code outbox.poller.fixed-delay-ms} if you want it externally tunable.
     */
    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:5000}")
    @Transactional
    public void poll() {
        List<OutboxEvent> pending = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) return;

        log.debug("Outbox poller found {} PENDING event(s) to publish", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // Deserialize payload back to a generic Map (preserves __TypeId__ header)
                Object payload = objectMapper.readValue(event.getPayload(), Map.class);

                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                throw new RuntimeException("Kafka send failed", ex);
                            }
                        });

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                log.info("Outbox published: id={} topic={} aggregateId={}",
                         event.getId(), event.getTopic(), event.getAggregateId());

            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus("FAILED");
                    log.error("Outbox event FAILED after {} retries: id={} topic={} aggregateId={} error={}",
                              MAX_RETRIES, event.getId(), event.getTopic(),
                              event.getAggregateId(), ex.getMessage());
                } else {
                    log.warn("Outbox publish attempt {} failed for id={}: {}",
                             event.getRetryCount(), event.getId(), ex.getMessage());
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
