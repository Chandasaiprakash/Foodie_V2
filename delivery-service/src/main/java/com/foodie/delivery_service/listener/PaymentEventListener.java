package com.foodie.delivery_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.correlation.CorrelationContext;
import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.delivery_service.deadletter.DeadLetterService;
import com.foodie.delivery_service.idempotency.IdempotencyService;
import com.foodie.delivery_service.model.Delivery;
import com.foodie.delivery_service.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Handles PaymentCompletedEvent — assigns a delivery partner.
 *
 * <p>Dead-letter replay safety: the {@code @DltHandler} persists exhausted
 * messages to MongoDB {@code dead_letters} so operators can replay via
 * {@code POST /internal/dead-letters/{id}/replay}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final DeliveryService deliveryService;
    private final IdempotencyService idempotencyService;
    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
        topics = "payment-completed",
        groupId = "delivery-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handle(
            PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        if (event.getCorrelationId() != null) {
            CorrelationContext.set(event.getCorrelationId());
        }

        if (!"SUCCESS".equalsIgnoreCase(event.getStatus())) {
            log.info("Payment not successful for order {} — skipping delivery assignment", event.getOrderUuid());
            return;
        }

        String eventId = "payment-completed-delivery::" + event.getOrderUuid();

        if (!idempotencyService.claim(eventId)) {
            log.debug("Duplicate PaymentCompletedEvent suppressed for delivery: {}", eventId);
            return;
        }

        Delivery d = deliveryService.assignForOrder(event);
        log.info("Assigned delivery id={} for order {}", d.getId(), d.getOrderUuid());
    }

    @DltHandler
    public void handleDlt(
            PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

        log.error("DLT: payment-completed (delivery) exhausted retries — storing for replay. topic={} orderUuid={}",
                  topic, event.getOrderUuid());

        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(event); }
        catch (Exception ex) { payloadJson = "{\"error\":\"serialisation failed\"}"; }

        deadLetterService.store(
            topic, key, payloadJson,
            event.getOrderUuid(), exceptionClass, exceptionMessage,
            4, event.getCorrelationId()
        );
    }
}
