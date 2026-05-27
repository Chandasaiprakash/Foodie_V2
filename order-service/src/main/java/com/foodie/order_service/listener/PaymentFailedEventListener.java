package com.foodie.order_service.listener;

import com.foodie.common.events.PaymentFailedEvent;
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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles PaymentFailedEvent → cancels order.
 *
 * <p>Dead-letter replay safety: DLT handler persists the failed message so
 * operators can replay it via {@code POST /internal/dead-letters/{id}/replay}
 * if the cancellation was incorrectly blocked by a transient error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedEventListener {

    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final DeadLetterService deadLetterService;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "payment-failed", groupId = "order-service-group")
    @Transactional
    public void handlePaymentFailed(
            PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        String eventId = "payment-failed::" + event.getOrderUuid();

        var order = orderRepository.findByOrderUuid(event.getOrderUuid())
            .orElseThrow(() -> {
                log.warn("No order found for UUID: {} - will retry payment failure", event.getOrderUuid());
                return new IllegalStateException("Order not found: " + event.getOrderUuid());
            });

        if (!idempotencyService.claim(eventId)) {
            return;
        }

        order.setPaymentStatus("FAILED");
        order.setStatus("CANCELLED");
        orderRepository.save(order);
        log.warn("Order {} CANCELLED - payment failed. Reason: {}",
                 order.getOrderUuid(), event.getReason());
    }

    @DltHandler
    public void handleDlt(
            PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

        log.error("DLT: payment-failed exhausted retries — storing for replay. topic={} orderUuid={}",
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
