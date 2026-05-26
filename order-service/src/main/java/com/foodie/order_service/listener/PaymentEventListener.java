package com.foodie.order_service.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.common.correlation.CorrelationContext;
import com.foodie.common.events.OrderUpdatedEvent;
import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.order_service.deadletter.DeadLetterService;
import com.foodie.order_service.deadletter.DltHandlerSupport;
import com.foodie.order_service.idempotency.IdempotencyService;
import com.foodie.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
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
 * Handles PaymentCompletedEvent — marks order CONFIRMED and emits OrderUpdatedEvent.
 *
 * <p><b>Dead-letter replay safety:</b> the {@code @DltHandler} now persists the
 * failed message to the {@code dead_letters} table so operators can inspect and
 * replay via {@code POST /internal/dead-letters/{id}/replay}.  The handler is
 * idempotent: re-delivery from the DLT itself will not create a duplicate row
 * (unique constraint on source_topic + original_key).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

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
    @KafkaListener(topics = "payment-completed", groupId = "order-service-group")
    @Transactional
    public void handlePaymentCompleted(
            PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        if (event.getCorrelationId() != null) {
            CorrelationContext.set(event.getCorrelationId());
        }

        String eventId = "payment-completed::" + event.getOrderUuid();

        if (!idempotencyService.claim(eventId)) {
            return;
        }

        orderRepository.findByOrderUuid(event.getOrderUuid())
            .ifPresentOrElse(order -> {
                order.setPaymentStatus(event.getStatus());
                order.setStatus("CONFIRMED");
                orderRepository.save(order);
                log.info("Order {} confirmed", order.getOrderUuid());

                OrderUpdatedEvent updatedEvent = OrderUpdatedEvent.builder()
                        .orderUuid(order.getOrderUuid())
                        .status(order.getStatus())
                        .paymentStatus(order.getPaymentStatus())
                        .customerEmail(order.getCustomerEmail())
                        .correlationId(CorrelationContext.get())
                        .build();
                kafkaTemplate.send("order-updated", updatedEvent);
                log.info("Sent OrderUpdatedEvent for {}", order.getOrderUuid());
            }, () -> {
                log.warn("No order found for UUID: {}", event.getOrderUuid());
                throw new IllegalStateException("Order not found: " + event.getOrderUuid());
            });
    }

    /**
     * Replay-safe DLT handler.
     *
     * <p>Captures the full event payload + exception metadata into {@code dead_letters}
     * so nothing is silently discarded. The unique constraint on
     * {@code (source_topic, original_key)} makes this handler safe to invoke
     * multiple times for the same message (DLT re-delivery or duplicate routing).
     */
    @DltHandler
    public void handleDlt(
            PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

        log.error("DLT: payment-completed exhausted retries — storing for replay. topic={} orderUuid={}",
                  topic, event.getOrderUuid());

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
