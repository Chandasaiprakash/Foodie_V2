package com.foodie.notification_service.filter;

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
 * Servlet filter — puts the correlation ID into MDC for every inbound HTTP
 * request and echoes it back on the response.
 *
 * The gateway always forwards X-Correlation-ID. If this service is called
 * directly (e.g. in tests or via internal Feign) and the header is missing,
 * a new UUID is generated so no log line ever lacks the field.
 *
 * Order = 1 so this runs before Spring Security and any business filters.
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
            CorrelationContext.clear(); // always remove from thread-local before returning thread to pool
        }
    }
}
