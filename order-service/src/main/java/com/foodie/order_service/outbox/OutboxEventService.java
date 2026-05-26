package com.foodie.order_service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Writes outbox rows inside the caller's active transaction.
 *
 * <p>PROPAGATION.MANDATORY ensures this method is never called outside a
 * transaction — if it were, the atomic guarantee (business change + outbox row
 * in one commit) would be lost, bringing back the dual-write problem.
 *
 * <p>Usage from {@code OrderService}:
 * <pre>{@code
 *   @Transactional
 *   public Order createOrder(...) {
 *       Order saved = orderRepository.save(order);           // step 1
 *       outboxEventService.save("order-created",             // step 2 — same TX
 *               saved.getOrderUuid(), event);
 *       return saved;
 *       // TX commits here — both rows are durable or neither is.
 *   }
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private static final int DEFAULT_TTL_HOURS = 72; // 3 days to publish or alert
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persist an outbox event in the current transaction.
     *
     * @param topic       Kafka topic
     * @param aggregateId Business key (orderUuid) — used as Kafka message key
     * @param payload     The event object to serialize to JSON
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, String aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(payload.getClass().getName())
                    .topic(topic)
                    .payload(json)
                    .status("PENDING")
                    .retryCount(0)
                    .expiresAt(Instant.now().plusSeconds(DEFAULT_TTL_HOURS * 3600L))
                    .build();
            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event saved: topic={} aggregateId={} type={}",
                      topic, aggregateId, payload.getClass().getSimpleName());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for aggregateId={}: {}", aggregateId, e.getMessage());
            throw new RuntimeException("Outbox serialization failure", e);
        }
    }
}
