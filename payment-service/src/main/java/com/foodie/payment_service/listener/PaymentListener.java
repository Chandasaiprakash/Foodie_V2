package com.foodie.payment_service.listener;

import com.foodie.common.correlation.CorrelationContext;
import com.foodie.common.events.OrderCreatedEvent;
import com.foodie.payment_service.deadletter.DeadLetterService;
import com.foodie.payment_service.idempotency.IdempotencyService;
import com.foodie.payment_service.service.PaymentService;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumes OrderCreatedEvent and creates a PENDING payment record.
 *
 * <p>Dead-letter replay safety: the {@code @DltHandler} persists exhausted
 * messages to {@code dead_letters} so operators can replay via
 * {@code POST /internal/dead-letters/{id}/replay}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "order-created", groupId = "payment-service-group")
    @Transactional
    public void handleOrderCreated(
            OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        if (event.getCorrelationId() != null) {
            CorrelationContext.set(event.getCorrelationId());
        }

        String eventId = "order-created::" + event.getOrderUuid();

        if (!idempotencyService.claim(eventId)) {
            return;
        }

        paymentService.processPayment(event);
        log.info("Payment record created for order {}", event.getOrderUuid());
    }

    @DltHandler
    public void handleDlt(
            OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage) {

        log.error("DLT: order-created exhausted retries — storing for replay. topic={} orderUuid={}",
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
