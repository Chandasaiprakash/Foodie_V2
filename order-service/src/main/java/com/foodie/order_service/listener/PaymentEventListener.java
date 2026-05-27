package com.foodie.order_service.listener;

import com.foodie.common.correlation.CorrelationContext;
import com.foodie.common.events.OrderUpdatedEvent;
import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.order_service.deadletter.DeadLetterService;
import com.foodie.order_service.deadletter.DltHandlerSupport;
import com.foodie.order_service.idempotency.IdempotencyService;
import com.foodie.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles PaymentCompletedEvent.
 *
 * Replay-safe + idempotent.
 * Duplicate events MUST short-circuit before any database mutation.
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

        final String eventId = "payment-completed::" + event.getOrderUuid();

        // CRITICAL:
        // Claim idempotency BEFORE any DB reads/writes.
        // Replay/DLQ duplicates must exit immediately.
        if (!idempotencyService.claim(eventId)) {
            log.info("Duplicate payment event skipped: {}", eventId);
            return;
        }

        var order = orderRepository.findByOrderUuid(event.getOrderUuid())
                .orElseThrow(() -> {
                    log.warn("No order found for UUID: {}", event.getOrderUuid());
                    return new IllegalStateException(
                            "Order not found: " + event.getOrderUuid()
                    );
                });

        // Extra replay protection.
        // If already confirmed, do not mutate again.
        if ("CONFIRMED".equals(order.getStatus())
                && event.getStatus().equals(order.getPaymentStatus())) {

            log.info(
                    "Order already confirmed for replayed event: {}",
                    order.getOrderUuid()
            );

            return;
        }

        order.setPaymentStatus(event.getStatus());
        order.setStatus("CONFIRMED");

        orderRepository.saveAndFlush(order);

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
    }

    @DltHandler
    public void handleDlt(
            PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false)
            String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false)
            String exceptionMessage) {

        log.error(
                "DLT: payment-completed exhausted retries — storing for replay. topic={} orderUuid={}",
                topic,
                event.getOrderUuid()
        );

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
