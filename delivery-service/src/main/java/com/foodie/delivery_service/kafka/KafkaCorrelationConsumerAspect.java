package com.foodie.delivery_service.kafka;

import com.foodie.common.correlation.CorrelationContext;
import com.foodie.common.correlation.OTelKafkaPropagation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * AOP aspect — wraps every {@code @KafkaListener} method to restore two
 * propagation channels from the Kafka message headers before the listener body
 * runs, and cleans them up afterwards.
 *
 * <h2>What is restored</h2>
 * <pre>
 *   1. W3C OTel trace context (traceparent / tracestate)
 *      Extracted via OTelKafkaPropagation.extract(). Makes the producer's span
 *      context current so any span Micrometer auto-records for the listener is
 *      a child of the producer's span — this is what creates the stitched
 *      waterfall view in Jaeger and Grafana Tempo.
 *
 *   2. Business correlation ID (X-Correlation-ID)
 *      Restored into MDC so every log line inside the listener carries the same
 *      correlationId that was assigned at the gateway for the original HTTP request.
 * </pre>
 *
 * <h2>Listener thread model</h2>
 * Kafka listeners in this codebase run on virtual threads
 * (see KafkaConsumerConfig). OTel context is stored in a thread-local-like
 * structure ({@code io.opentelemetry.context.Context}) that is compatible with
 * virtual threads — no additional setup is required.
 *
 * <h2>No listener code changes needed</h2>
 * This aspect fires automatically for every method annotated with
 * {@code @KafkaListener}. Listener implementations remain clean of propagation
 * boilerplate.
 */
@Aspect
@Component
public class KafkaCorrelationConsumerAspect {

    private static final Logger log = LoggerFactory.getLogger(KafkaCorrelationConsumerAspect.class);

    @Around("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public Object restoreContext(ProceedingJoinPoint pjp) throws Throwable {
        Headers kafkaHeaders = extractHeaders(pjp.getArgs());

        // ── 1. Restore OTel trace context ───────────────────────────────────
        // extract() reads "traceparent" from the Kafka headers and makes the
        // producer's span context current on this thread for the duration of
        // the try block. The returned Scope MUST be closed to restore the
        // previous context (null-safe: extract() returns a no-op Scope when
        // no traceparent header is present).
        try (var otelScope = (kafkaHeaders != null)
                ? OTelKafkaPropagation.extract(kafkaHeaders)
                : io.opentelemetry.context.Context.current().makeCurrent()) {

            // ── 2. Restore business correlation ID ──────────────────────────
            String correlationId = (kafkaHeaders != null)
                    ? extractCorrelationId(kafkaHeaders)
                    : null;
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
                log.debug("No correlation ID in Kafka headers — generated: {}", correlationId);
            }

            try {
                CorrelationContext.set(correlationId);
                return pjp.proceed();
            } finally {
                CorrelationContext.clear();
            }
        }
    }

    // ── Header extraction helpers ─────────────────────────────────────────────

    private Headers extractHeaders(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ConsumerRecord<?, ?> record) {
                return record.headers();
            }
            if (arg instanceof Message<?> msg) {
                // Spring Kafka wraps ConsumerRecord headers in a KafkaHeaderMapper.
                // The native header accessor gives direct access to Kafka Headers.
                Object nativeHeaders = msg.getHeaders()
                        .get(org.springframework.kafka.support.KafkaHeaders.NATIVE_HEADERS);
                if (nativeHeaders instanceof Headers h) {
                    return h;
                }
            }
        }
        return null;
    }

    private String extractCorrelationId(Headers headers) {
        Header header = headers.lastHeader(CorrelationContext.KAFKA_HEADER);
        return (header != null)
                ? new String(header.value(), StandardCharsets.UTF_8)
                : null;
    }
}
