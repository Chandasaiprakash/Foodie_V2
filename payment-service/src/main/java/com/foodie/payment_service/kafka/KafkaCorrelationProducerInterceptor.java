package com.foodie.payment_service.kafka;

import com.foodie.common.correlation.CorrelationContext;
import com.foodie.common.correlation.OTelKafkaPropagation;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Producer Interceptor — stamps two propagation channels onto every
 * outgoing Kafka message header so the full trace context flows end-to-end.
 *
 * <h2>Headers written</h2>
 * <pre>
 *   X-Correlation-ID  — business-level UUID (set at the gateway, stable for
 *                       the life of a user request). Used in logs and by
 *                       support teams. Propagated by CorrelationContext.
 *
 *   traceparent        — W3C Trace Context header (version-traceId-parentId-flags).
 *   tracestate         — W3C vendor-specific trace state (optional, may be empty).
 *                       Both are injected by OTelKafkaPropagation using the
 *                       current OTel span so Jaeger / Grafana Tempo can link
 *                       the producer span to the consumer span in a waterfall.
 * </pre>
 *
 * <h2>Why an interceptor instead of manual header setting</h2>
 * The interceptor fires for every {@code KafkaTemplate.send()} and every
 * OutboxPoller publish — zero per-call boilerplate. It also fires for retry
 * topic redeliveries from the @RetryableTopic infrastructure.
 *
 * Registration: set {@code spring.kafka.producer.properties.interceptor.classes}
 * in application.properties to this class's fully-qualified name.
 */
public class KafkaCorrelationProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        Headers headers = record.headers();

        // ── 1. Business correlation ID ──────────────────────────────────────
        String correlationId = CorrelationContext.get();
        if (correlationId == null || correlationId.isBlank()) {
            // OutboxPoller runs on a scheduler thread outside any HTTP request;
            // generate a fresh UUID so the header is never absent.
            correlationId = UUID.randomUUID().toString();
        }
        headers.remove(CorrelationContext.KAFKA_HEADER);
        headers.add(CorrelationContext.KAFKA_HEADER,
                    correlationId.getBytes(StandardCharsets.UTF_8));

        // ── 2. OTel W3C trace context (traceparent / tracestate) ────────────
        // OTelKafkaPropagation reads the active OTel span from the current
        // Context and serialises it as a W3C traceparent header. On the
        // consumer side, KafkaCorrelationConsumerAspect extracts this header
        // and makes the span context current so the listener span is recorded
        // as a child of the producer span in Jaeger / Tempo.
        OTelKafkaPropagation.inject(headers);

        return record;
    }

    @Override public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}
    @Override public void close() {}
    @Override public void configure(Map<String, ?> configs) {}
}
