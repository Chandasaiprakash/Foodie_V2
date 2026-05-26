package com.foodie.notification_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.correlation.CorrelationContext;
import com.foodie.common.events.DeliveryEvent;
import com.foodie.common.events.OrderUpdatedEvent;
import com.foodie.notification_service.deadletter.DeadLetterService;
import com.foodie.notification_service.idempotency.IdempotencyService;
import com.foodie.notification_service.service.NotificationService;
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
 * Consumes order and delivery events and broadcasts them via WebSocket.
 *
 * <p>Dead-letter replay safety: both listeners now have {@code @RetryableTopic}
 * and {@code @DltHandler}. Exhausted messages are persisted in Redis via
 * {@code DeadLetterService} so operators can inspect and replay via
 * {@code POST /internal/dead-letters/{id}/replay}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlt"
    )
    @KafkaListener(
        topics = "order-updated",
        groupId = "notification-service-group",
        containerFactory = "orderUpdatedEventListenerFactory"
    )
    public void handleOrderUpdatedEvent(
            OrderUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        if (event.getCorrelationId() != null) {
            CorrelationContext.set(event.getCorrelationId());
        }

        String eventId = "order-updated::" + event.getOrderUuid() + "::" + event.getStatus();

        if (!idempotencyService.claim(eventId)) {
            log.debug("Duplicate OrderUpdatedEvent notification suppressed: {}", eventId);
            return;
        }

        log.info("Broadcasting OrderUpdatedEvent: orderUuid={} status={}", event.getOrderUuid(), event.getStatus());
        notificationService.broadcast(event);
    }

    @DltHandler
    public void handleOrderUpdatedDlt(
            OrderUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

        log.error("DLT: order-updated exhausted retries — storing for replay. topic={} orderUuid={}",
                  topic, event.getOrderUuid());

        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(event); }
        catch (Exception ex) { payloadJson = "{\"error\":\"serialisation failed\"}"; }

        deadLetterService.store(topic, key, payloadJson, event.getOrderUuid(),
                                exceptionClass, exceptionMessage, 3, event.getCorrelationId());
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "delivery-events", groupId = "notification-service-group")
    public void handleDeliveryEvent(
            DeliveryEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        if (event.getCorrelationId() != null) {
            CorrelationContext.set(event.getCorrelationId());
        }

        String eventId = "delivery-event::" + event.getOrderUuid() + "::" + event.getStatus();

        if (!idempotencyService.claim(eventId)) {
            log.debug("Duplicate DeliveryEvent notification suppressed: {}", eventId);
            return;
        }

        log.info("Broadcasting DeliveryEvent: orderUuid={} status={}", event.getOrderUuid(), event.getStatus());
        notificationService.broadcast(event);
    }

    @DltHandler
    public void handleDeliveryDlt(
            DeliveryEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

        log.error("DLT: delivery-events exhausted retries — storing for replay. topic={} orderUuid={} status={}",
                  topic, event.getOrderUuid(), event.getStatus());

        String payloadJson;
        try { payloadJson = objectMapper.writeValueAsString(event); }
        catch (Exception ex) { payloadJson = "{\"error\":\"serialisation failed\"}"; }

        deadLetterService.store(topic, key, payloadJson, event.getOrderUuid(),
                                exceptionClass, exceptionMessage, 3, event.getCorrelationId());
    }
}
