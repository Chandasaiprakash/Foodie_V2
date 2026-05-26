package com.foodie.gateway_service.filter;

import com.foodie.common.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gateway Global Filter — Correlation ID origin point.
 *
 * Runs at the highest precedence (order = -10) so every subsequent filter
 * and every downstream service sees the ID.
 *
 * Rules:
 *   - If the inbound request already carries X-Correlation-ID (e.g. from a
 *     mobile client or an internal retry), that value is honoured.
 *   - Otherwise a new UUID is generated here.
 *
 * The ID is:
 *   1. Forwarded to downstream services as X-Correlation-ID request header.
 *   2. Echoed back to the caller as X-Correlation-ID response header
 *      so clients can log it for support tickets.
 *   3. Added to the reactive MDC context so gateway-side log lines carry it.
 *      (WebFlux doesn't use ThreadLocal MDC — we use contextWrite for this.)
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public int getOrder() {
        return -10; // Run before JWT filter (Ordered.HIGHEST_PRECEDENCE is Integer.MIN_VALUE)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CorrelationContext.HEADER_NAME);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;

        // Stamp the ID onto the forwarded request
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CorrelationContext.HEADER_NAME, finalCorrelationId)
                .build();

        // Echo it back on the response so the caller can reference it
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(CorrelationContext.HEADER_NAME, finalCorrelationId);

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        log.debug("Correlation ID assigned: {}", finalCorrelationId);

        // contextWrite propagates the ID into the reactive pipeline (Reactor Context)
        // so it is visible in gateway-side log enrichment hooks
        return chain.filter(mutatedExchange)
                .contextWrite(ctx -> ctx.put(CorrelationContext.MDC_KEY, finalCorrelationId));
    }
}
