package com.foodie.delivery_service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.events.DeliveryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Poller — delivery-service.
 *
 * <p>Polls MongoDB for PENDING outbox events every 5 seconds and publishes them
 * to Kafka. Provides at-least-once delivery with retry up to MAX_RETRIES.
 *
 * @see OutboxEvent
 * @see OutboxEventService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int BATCH_SIZE  = 100;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, DeliveryEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.poller.fixed-delay-ms:5000}")
    public void poll() {
        List<OutboxEvent> pending = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc("PENDING", PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) return;

        log.debug("Outbox poller found {} PENDING delivery event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                DeliveryEvent payload = objectMapper.readValue(event.getPayload(), DeliveryEvent.class);

                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload)
                        .whenComplete((result, ex) -> {
                            if (ex != null) throw new RuntimeException("Kafka send failed", ex);
                        });

                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                log.info("Outbox published: id={} topic={} aggregateId={}",
                         event.getId(), event.getTopic(), event.getAggregateId());

            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus("FAILED");
                    log.error("Outbox event FAILED after {} retries: id={} aggregateId={} error={}",
                              MAX_RETRIES, event.getId(), event.getAggregateId(), ex.getMessage());
                } else {
                    log.warn("Outbox publish attempt {} failed for id={}: {}",
                             event.getRetryCount(), event.getId(), ex.getMessage());
                }
            }
            outboxEventRepository.save(event);
        }
    }
}
