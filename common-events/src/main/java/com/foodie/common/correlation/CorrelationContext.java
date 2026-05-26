package com.foodie.common.correlation;

import org.slf4j.MDC;

/**
 * Central holder for the two propagation channels in the Foodie stack.
 *
 * <h2>Two distinct IDs travel together</h2>
 * <pre>
 *   X-Correlation-ID (business ID)
 *     - A stable UUID assigned at the gateway for the lifetime of a user request.
 *     - Visible to clients in response headers; safe to hand to support teams.
 *     - Survives restarts, retries, and async hops. One ID per user action.
 *
 *   OTel trace context  (W3C traceparent / tracestate)
 *     - A 128-bit trace ID + 64-bit span ID managed by Micrometer/OTel.
 *     - Used by Jaeger / Grafana Tempo to build waterfall span diagrams.
 *     - Different from the correlation ID: a new child span is created on
 *       every service boundary; the trace ID stays the same, the span ID changes.
 * </pre>
 *
 * <h2>Propagation channels</h2>
 * <pre>
 *   HTTP  — X-Correlation-ID header (this class) + traceparent header (OTel auto-instrumentation)
 *   Kafka — X-Correlation-ID header (KafkaCorrelationProducerInterceptor)
 *           + traceparent / tracestate headers (OTelKafkaPropagationUtil)
 *   MDC   — correlationId, traceId, spanId (last two auto-set by Micrometer Tracing bridge)
 * </pre>
 */
public final class CorrelationContext {

    /** HTTP and Kafka header name for the business correlation ID. */
    public static final String HEADER_NAME  = "X-Correlation-ID";

    /** MDC key for the business correlation ID. */
    public static final String MDC_KEY      = "correlationId";

    /** Kafka header name for the business correlation ID (same value as HEADER_NAME). */
    public static final String KAFKA_HEADER = "X-Correlation-ID";

    /**
     * W3C Trace Context header names — propagated on Kafka messages so OTel
     * can link producer and consumer spans into a single trace in Jaeger/Tempo.
     *
     * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
     */
    public static final String KAFKA_TRACEPARENT  = "traceparent";
    public static final String KAFKA_TRACESTATE   = "tracestate";

    private CorrelationContext() {}

    /** Sets the business correlation ID into MDC. Thread-safe (MDC is ThreadLocal). */
    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    /** Returns the business correlation ID from MDC, or {@code null} if not set. */
    public static String get() {
        return MDC.get(MDC_KEY);
    }

    /** Removes the business correlation ID from MDC. Always call in a finally block. */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
