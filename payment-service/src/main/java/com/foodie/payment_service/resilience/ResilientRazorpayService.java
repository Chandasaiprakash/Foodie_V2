package com.foodie.payment_service.resilience;

import com.foodie.payment_service.service.RazorpayService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * Resilient wrapper around {@link RazorpayService}.
 *
 * <p>Razorpay is an external third-party API.  Without protection:
 * <ul>
 *   <li>A Razorpay outage causes every {@code /payments/create} call to hang
 *       until the HTTP timeout (often 30+ seconds), exhausting the Tomcat
 *       thread pool and taking down the entire payment-service.</li>
 *   <li>Razorpay applies rate limits; without a bulkhead we can accidentally
 *       fire hundreds of concurrent requests and get throttled or banned.</li>
 * </ul>
 *
 * <p>Resilience chain:
 * <ol>
 *   <li><b>Bulkhead</b> — max 10 concurrent calls to Razorpay.</li>
 *   <li><b>CircuitBreaker</b> — opens at 40% failure rate, waits 30s before
 *       trying again.  This is tighter than internal services because external
 *       APIs can stay degraded for minutes.</li>
 *   <li><b>Retry</b> — 3 attempts with exponential backoff (1s, 2s, 4s),
 *       only on {@link java.io.IOException} / {@link java.net.ConnectException}.
 *       We do NOT retry on Razorpay business errors (e.g., invalid key).</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientRazorpayService {

    private final RazorpayService razorpayService;

    /** Thrown when the Razorpay circuit is open or bulkhead is full. */
    public static class RazorpayUnavailableException extends RuntimeException {
        public RazorpayUnavailableException(String msg) { super(msg); }
        public RazorpayUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    /**
     * Create a Razorpay order — protected by Bulkhead → CircuitBreaker → Retry.
     *
     * @param orderUuid the internal order UUID used as Razorpay receipt
     * @param amount    the payment amount in INR
     * @return Razorpay order JSON
     * @throws RazorpayUnavailableException if all resilience fallbacks fire
     */
    @Bulkhead(name = "razorpay", fallbackMethod = "createOrderFallback")
    @CircuitBreaker(name = "razorpay", fallbackMethod = "createOrderFallback")
    @Retry(name = "razorpay", fallbackMethod = "createOrderFallback")
    public JSONObject createOrder(String orderUuid, double amount) throws Exception {
        return razorpayService.createOrder(orderUuid, amount);
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    public JSONObject createOrderFallback(
            String orderUuid, double amount,
            io.github.resilience4j.bulkhead.BulkheadFullException ex) {
        log.warn("Razorpay bulkhead full — orderUuid={}", orderUuid);
        throw new RazorpayUnavailableException(
                "Payment gateway is currently overloaded. Please try again shortly.", ex);
    }

    public JSONObject createOrderFallback(
            String orderUuid, double amount,
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("Razorpay circuit OPEN — orderUuid={}", orderUuid);
        throw new RazorpayUnavailableException(
                "Payment gateway is temporarily unavailable. Please try again in a moment.", ex);
    }

    public JSONObject createOrderFallback(
            String orderUuid, double amount, Throwable ex) {
        log.error("Razorpay createOrder failed after retries — orderUuid={}: {}",
                  orderUuid, ex.getMessage());
        throw new RazorpayUnavailableException(
                "Unable to reach payment gateway. Please try again.", ex);
    }
}
