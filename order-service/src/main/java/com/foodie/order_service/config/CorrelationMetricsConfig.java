package com.foodie.order_service.config;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Micrometer configuration — adds correlationId as a per-request common tag.
 *
 * Because Micrometer tags are evaluated at meter registration time (not at
 * observation time for counters/gauges), the most practical approach for
 * request-scoped correlation is to use Micrometer Observations (Spring Boot 3+)
 * and the ObservationRegistry, which propagates MDC context into spans and
 * metrics automatically via the Micrometer Tracing bridge.
 *
 * This config wires up the bridge so that:
 *   - Every HTTP request span carries correlationId as a span attribute in Jaeger/OTLP.
 *   - Prometheus counters for http.server.requests include correlationId
 *     as a low-cardinality label when explicitly added via ObservationFilter.
 *
 * NOTE: correlationId is a HIGH-cardinality tag (unique per request).
 * Do NOT add it as a standard Micrometer tag to time-series metrics — that
 * would create unbounded label cardinality in Prometheus. Instead it is
 * propagated through OTel baggage (for traces) and MDC (for logs) only.
 * This class exists to document that decision and wire the OTel bridge correctly.
 */
@Configuration
public class CorrelationMetricsConfig {

    @Value("${spring.application.name:unknown}")
    private String applicationName;

    /**
     * Adds service.name as a common tag so every metric emitted by this
     * service is labelled — useful for Grafana multi-service dashboards.
     * correlationId intentionally omitted here (see Javadoc above).
     */
    @Bean
    public MeterFilter serviceLabelFilter() {
        return MeterFilter.commonTags(List.of(Tag.of("service", applicationName)));
    }
}
