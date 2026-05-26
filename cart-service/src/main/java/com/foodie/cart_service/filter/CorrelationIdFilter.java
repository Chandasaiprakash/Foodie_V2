package com.foodie.cart_service.filter;

import com.foodie.common.correlation.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter — puts the correlation ID from X-Correlation-ID into MDC
 * for every inbound HTTP request and echoes it back on the response.
 *
 * <p>Micrometer Tracing (OTel bridge) also populates {@code traceId} and
 * {@code spanId} into MDC automatically for every recorded span. This filter
 * handles the separate business-level correlation ID that flows end-to-end
 * through the Foodie stack independently of the OTel trace context.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationContext.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            CorrelationContext.set(correlationId);
            response.setHeader(CorrelationContext.HEADER_NAME, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
        }
    }
}
