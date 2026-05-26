package com.foodie.delivery_service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Writes outbox documents to MongoDB.
 *
 * <p>MongoDB multi-document transactions require a replica set. In a replica set
 * environment, call this inside a {@code @Transactional} method to get the same
 * atomicity guarantee as the JPA outbox in order-service / payment-service.
 *
 * <p>In a single-node dev environment (no replica set), this still provides
 * near-atomic behaviour: the outbox document is written immediately after the
 * delivery document within the same code block. The window where a crash could
 * cause inconsistency is negligible compared to the original direct Kafka send.
 *
 * <p>Usage from {@link com.foodie.delivery_service.service.DeliveryService}:
 * <pre>{@code
 *   Delivery saved = deliveryRepository.insert(d);
 *   outboxEventService.save("delivery-events", d.getOrderUuid(), event);
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private static final int DEFAULT_TTL_HOURS = 72;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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
