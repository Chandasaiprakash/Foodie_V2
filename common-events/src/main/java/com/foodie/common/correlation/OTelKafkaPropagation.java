package com.foodie.common.correlation;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for propagating W3C trace context (traceparent / tracestate) through
 * Kafka message headers, enabling Jaeger and Grafana Tempo to stitch producer
 * and consumer spans into a continuous trace.
 *
 * <h2>Why this is needed</h2>
 * HTTP services benefit from OTel auto-instrumentation: the Micrometer bridge
 * transparently injects {@code traceparent} into outbound HTTP headers and
 * extracts it from inbound ones. Kafka has no such built-in support in the
 * Micrometer OTel bridge — context must be injected/extracted manually.
 *
 * <h2>Flow (producer side)</h2>
 * <pre>
 *   KafkaCorrelationProducerInterceptor.onSend()
 *     → OTelKafkaPropagation.inject(record.headers())
 *         → reads current OTel Context (which carries the active span)
 *         → writes "traceparent" + "tracestate" headers onto the Kafka record
 * </pre>
 *
 * <h2>Flow (consumer side)</h2>
 * <pre>
 *   KafkaCorrelationConsumerAspect.restoreCorrelationId()
 *     → OTelKafkaPropagation.extract(record.headers())
 *         → reads "traceparent" from Kafka headers
 *         → creates a child OTel Context linked to the producer's span
 *         → makes the context current for the listener thread
 *     → Micrometer auto-records the listener execution as a child span
 * </pre>
 *
 * <h2>Result in Jaeger / Tempo</h2>
 * A single HTTP request that triggers an order → payment → delivery fanout
 * shows as one waterfall trace:
 * <pre>
 *   gateway (HTTP) ──► order-service (HTTP handler)
 *                          └──► [Kafka publish: order-created]
 *                                   └──► payment-service (Kafka listener)
 *                                            └──► [Kafka publish: payment-completed]
 *                                                     ├──► order-service (Kafka listener)
 *                                                     └──► delivery-service (Kafka listener)
 * </pre>
 */
public final class OTelKafkaPropagation {

    private OTelKafkaPropagation() {}

    /**
     * Injects the current OTel span context into Kafka headers as W3C traceparent.
     * Call this in a producer interceptor's {@code onSend()} after the span is active.
     */
    public static void inject(Headers headers) {
        GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(Context.current(), headers, KAFKA_SETTER);
    }

    /**
     * Extracts the W3C trace context from Kafka headers and makes it the current
     * context for the calling thread. Returns a {@link io.opentelemetry.context.Scope}
     * that MUST be closed in a finally block to restore the previous context.
     *
     * <pre>{@code
     * try (var scope = OTelKafkaPropagation.extract(record.headers())) {
     *     // listener body — any span created here is a child of the producer's span
     * }
     * }</pre>
     */
    public static io.opentelemetry.context.Scope extract(Headers headers) {
        Context extracted = GlobalOpenTelemetry.getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(), headers, KAFKA_GETTER);
        return extracted.makeCurrent();
    }

    /**
     * Returns the trace ID string from Kafka headers, or {@code null} if absent.
     * Used for MDC enrichment when manual extraction is needed.
     */
    public static String extractTraceId(Headers headers) {
        try (var scope = extract(headers)) {
            SpanContext spanCtx = Span.current().getSpanContext();
            return spanCtx.isValid() ? spanCtx.getTraceId() : null;
        }
    }

    // ── TextMapSetter: writes OTel context into Kafka Headers ────────────────

    private static final TextMapSetter<Headers> KAFKA_SETTER = (headers, key, value) -> {
        if (headers != null && key != null && value != null) {
            headers.remove(key); // idempotent on retry
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    };

    // ── TextMapGetter: reads OTel context from Kafka Headers ─────────────────

    private static final TextMapGetter<Headers> KAFKA_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers headers) {
            List<String> keys = new ArrayList<>();
            headers.forEach(h -> keys.add(h.key()));
            return keys;
        }

        @Override
        public String get(Headers headers, String key) {
            if (headers == null) return null;
            Header header = headers.lastHeader(key);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    };
}
