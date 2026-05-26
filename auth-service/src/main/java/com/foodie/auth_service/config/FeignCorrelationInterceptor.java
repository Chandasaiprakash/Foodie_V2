package com.foodie.auth_service.config;

import com.foodie.common.correlation.CorrelationContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

/**
 * Feign RequestInterceptor — propagates the current correlation ID onto every
 * outbound Feign HTTP call as the X-Correlation-ID header.
 *
 * This ensures that when delivery-service calls order-service (or auth-service
 * calls user-service) the receiving service's CorrelationIdFilter picks up
 * the same ID and all log lines share the same correlationId across the
 * synchronous service-to-service call.
 */
@Component
public class FeignCorrelationInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = CorrelationContext.get();
        if (correlationId != null && !correlationId.isBlank()) {
            template.header(CorrelationContext.HEADER_NAME, correlationId);
        }
    }
}
