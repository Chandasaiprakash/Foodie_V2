package com.foodie.order_service.listener;

import com.foodie.common.events.DeliveryEvent;
import com.foodie.common.events.OrderUpdatedEvent;
import com.foodie.order_service.deadletter.DeadLetterService;
import com.foodie.order_service.deadletter.DltHandlerSupport;
import com.foodie.order_service.idempotency.IdempotencyService;
import com.foodie.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles DeliveryEvent → updates Order.status and emits OrderUpdatedEvent.
 *
 * <p>Dead-letter replay safety: the {@code @DltHandler} persists exhausted
 * messages to {@code dead_letters} for operator review and replay via
 * {@code POST /internal/dead-letters/{id}/replay}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventListener {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final IdempotencyService idempotencyService;
    private final DeadLetterService deadLetterService;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "delivery-events", groupId = "order-service-group")
    @Transactional
    public void handleDeliveryEvent(
            DeliveryEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        String eventId = "delivery-event::" + event.getOrderUuid() + "::" + event.getStatus();

        var order = orderRepository.findByOrderUuid(event.getOrderUuid())
            .orElseThrow(() -> {
                log.warn("No order found for UUID: {} - will retry", event.getOrderUuid());
                return new IllegalStateException("Order not found: " + event.getOrderUuid());
            });

        if (!idempotencyService.claim(eventId)) {
            log.debug("Duplicate DeliveryEvent suppressed: {}", eventId);
            return;
        }

        order.setStatus(event.getStatus());
        orderRepository.save(order);
        log.info("Order {} status updated to {} via DeliveryEvent", order.getOrderUuid(), event.getStatus());

        OrderUpdatedEvent updatedEvent = OrderUpdatedEvent.builder()
                .orderUuid(order.getOrderUuid())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .customerEmail(order.getCustomerEmail())
                .build();

        kafkaTemplate.send("order-updated", updatedEvent);
        log.info("Published OrderUpdatedEvent after delivery update for {}", order.getOrderUuid());
    }

    @DltHandler
    public void handleDlt(
            DeliveryEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

        log.error("DLT: delivery-events exhausted retries — storing for replay. topic={} orderUuid={} status={}",
                  topic, event.getOrderUuid(), event.getStatus());

        deadLetterService.store(
            topic,
            key,
            DltHandlerSupport.toJson(event),
            event.getOrderUuid(),
            exceptionClass,
            exceptionMessage,
            4,
            event.getCorrelationId()
        );
    }
}
