package com.foodie.delivery_service.resilience;

import com.foodie.delivery_service.client.OrderServiceClient;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resilient wrapper around the raw {@link OrderServiceClient} Feign interface.
 *
 * <p>Why a wrapper rather than annotating the Feign interface directly?
 * Feign interfaces are proxied by Spring Cloud OpenFeign; adding Resilience4j
 * annotations directly on the interface methods requires an extra AOP proxy
 * that can cause subtle ordering issues.  A dedicated {@code @Component}
 * wrapper is the cleanest approach: it is a concrete Spring bean, so AOP
 * proxying is straightforward and the fallback methods are easy to unit-test.
 *
 * <p>Resilience chain applied (in priority order):
 * <ol>
 *   <li><b>Bulkhead</b> — limits concurrent calls to 20 so a slow
 *       order-service cannot exhaust the delivery-service thread pool.</li>
 *   <li><b>CircuitBreaker</b> — opens after 50% failure rate and stops
 *       hammering a down order-service, falling back to {@link Optional#empty()}
 *       so the caller gracefully skips the enrichment step.</li>
 *   <li><b>Retry</b> — 2 attempts with 500ms wait, only on network-level
 *       exceptions.  We intentionally do NOT retry on 4xx/5xx HTTP errors
 *       to avoid amplifying load on a struggling service.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientOrderServiceClient {

    private final OrderServiceClient orderServiceClient;

    /**
     * Fetch order details from order-service, protected by CircuitBreaker,
     * Retry, and Bulkhead.
     *
     * @param orderUuid the order to look up
     * @return {@link Optional} containing the OrderDto, or empty if the call
     *         fails / the circuit is open
     */
    @Bulkhead(name = "orderServiceClient", fallbackMethod = "fetchFallback")
    @CircuitBreaker(name = "orderServiceClient", fallbackMethod = "fetchFallback")
    @Retry(name = "orderServiceClient", fallbackMethod = "fetchFallback")
    public Optional<OrderServiceClient.OrderDto> fetchOrder(String orderUuid) {
        OrderServiceClient.OrderDto dto = orderServiceClient.getOrder(orderUuid);
        return Optional.ofNullable(dto);
    }

    // ── Fallback methods ────────────────────────────────────────────────────
    // Each Resilience4j fallback must accept the *same* parameters as the
    // protected method, plus a final Throwable parameter.

    /** Bulkhead fallback — too many concurrent calls */
    public Optional<OrderServiceClient.OrderDto> fetchFallback(
            String orderUuid,
            io.github.resilience4j.bulkhead.BulkheadFullException ex) {
        log.warn("Bulkhead full for orderServiceClient — orderUuid={}: {}",
                 orderUuid, ex.getMessage());
        return Optional.empty();
    }

    /** CircuitBreaker fallback — circuit is OPEN */
    public Optional<OrderServiceClient.OrderDto> fetchFallback(
            String orderUuid,
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("Circuit OPEN for orderServiceClient — orderUuid={}: {}",
                 orderUuid, ex.getMessage());
        return Optional.empty();
    }

    /** Generic fallback — Retry exhausted or unexpected exception */
    public Optional<OrderServiceClient.OrderDto> fetchFallback(
            String orderUuid, Throwable ex) {
        log.warn("orderServiceClient call failed for orderUuid={}: {}",
                 orderUuid, ex.getMessage());
        return Optional.empty();
    }
}
