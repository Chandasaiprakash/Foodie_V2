package com.foodie.payment_service.controller;

import com.foodie.payment_service.model.Payment;
import com.foodie.payment_service.resilience.ResilientRazorpayService;
import com.foodie.payment_service.service.PaymentService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Payment controller — all Razorpay calls now go through
 * {@link ResilientRazorpayService} (CircuitBreaker + Bulkhead + Retry).
 *
 * /payments/create and /payments/verify are additionally protected by a
 * RateLimiter so a burst (e.g., frontend retry loop, stress test) cannot
 * exhaust the Razorpay API quota.
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final ResilientRazorpayService razorpayService;  // ← resilient wrapper

    @Value("${razorpay.key-id}")
    private String razorKey;

    // ── Manual payment (testing) ─────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Payment> manualPay(@RequestBody Payment payment) {
        return ResponseEntity.ok(paymentService.manualPayment(payment));
    }

    // ── Get by customer ───────────────────────────────────────────────────────
    @GetMapping("/customer/{customerEmail}")
    public ResponseEntity<List<Payment>> getByCustomerEmail(
            @PathVariable String customerEmail) {
        List<Payment> payments = paymentService.getByCustomerEmail(customerEmail);
        return payments.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(payments);
    }

    // ── Update ────────────────────────────────────────────────────────────────
    @PutMapping("/{paymentUuid}")
    public ResponseEntity<Payment> updatePayment(
            @PathVariable String paymentUuid,
            @RequestBody Payment paymentUpdate) {
        Payment updated = paymentService.updatePayment(paymentUuid, paymentUpdate);
        return updated == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(updated);
    }

    // ── Get by order ──────────────────────────────────────────────────────────
    @GetMapping("/order/{orderUuid}")
    public ResponseEntity<List<Payment>> getByOrder(@PathVariable String orderUuid) {
        List<Payment> payments = paymentService.getByOrderUuid(orderUuid);
        return payments.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(payments);
    }

    // ── Step 1: Create Razorpay order ─────────────────────────────────────────
    /**
     * RateLimiter: max 50 req/sec.  Bulkhead + CircuitBreaker + Retry are
     * handled inside {@link ResilientRazorpayService#createOrder}.
     */
    @PostMapping("/create")
    @RateLimiter(name = "paymentCreate", fallbackMethod = "paymentCreateRateLimitFallback")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String orderUuid    = (String) body.get("orderUuid");
        double amount       = Double.parseDouble(body.get("amount").toString());
        String customerEmail = (String) body.get("customerEmail");

        log.info("Creating Razorpay order for orderUuid={} amount={}", orderUuid, amount);

        paymentService.createPendingForOrder(orderUuid, customerEmail, amount);

        JSONObject razorOrder;
        try {
            razorOrder = razorpayService.createOrder(orderUuid, amount);
        } catch (ResilientRazorpayService.RazorpayUnavailableException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error creating Razorpay order: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to create payment order"));
        }

        return ResponseEntity.ok(Map.of(
                "razorKey", razorKey,
                "orderId",  razorOrder.getString("id"),
                "amount",   razorOrder.getInt("amount"),
                "currency", razorOrder.getString("currency"),
                "receipt",  orderUuid
        ));
    }

    // ── Step 2: Verify payment success ────────────────────────────────────────
    @PostMapping("/verify")
    @RateLimiter(name = "paymentVerify", fallbackMethod = "paymentVerifyRateLimitFallback")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        try {
            String orderUuid = body.get("receipt") != null
                    ? body.get("receipt") : body.get("orderUuid");
            if (orderUuid == null)
                return ResponseEntity.badRequest().body(Map.of("error", "orderUuid missing"));

            log.info("Verifying successful payment for order {}", orderUuid);
            paymentService.markSuccess(orderUuid);
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Payment verified successfully"));
        } catch (Exception e) {
            log.error("Error verifying payment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Verification failed", "details", e.getMessage()));
        }
    }

    // ── Step 3: Mark payment failed ───────────────────────────────────────────
    @PostMapping("/fail")
    public ResponseEntity<?> markFailed(@RequestBody Map<String, String> body) {
        try {
            String orderUuid = body.get("orderUuid");
            String reason = body.getOrDefault("reason", "Unknown failure");
            if (orderUuid == null)
                return ResponseEntity.badRequest().body(Map.of("error", "orderUuid missing"));

            log.warn("Marking payment as failed for order {} - Reason: {}", orderUuid, reason);
            paymentService.markFailed(orderUuid, reason);
            return ResponseEntity.ok(Map.of("status", "FAILED", "message", reason));
        } catch (Exception e) {
            log.error("Error marking payment failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Payment failure update failed", "details", e.getMessage()));
        }
    }

    // ── Step 4: Razorpay Webhook ──────────────────────────────────────────────
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody Map<String, Object> payload) {
        try {
            log.info("Received Razorpay Webhook: {}", payload);

            String event = (String) payload.get("event");
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadData = (Map<String, Object>) payload.get("payload");
            @SuppressWarnings("unchecked")
            Map<String, Object> orderObj    = (Map<String, Object>) payloadData.get("order");
            @SuppressWarnings("unchecked")
            Map<String, Object> orderData   = (Map<String, Object>) orderObj.get("entity");

            String orderUuid = (String) orderData.get("receipt");
            if (orderUuid == null)
                return ResponseEntity.badRequest().body(Map.of("error", "orderUuid missing from webhook"));

            if ("payment.captured".equals(event)) {
                paymentService.markSuccess(orderUuid);
                log.info("Webhook: Payment captured for order {}", orderUuid);
            } else if ("payment.failed".equals(event)) {
                paymentService.markFailed(orderUuid, "Payment failed via webhook");
                log.warn("Webhook: Payment failed for order {}", orderUuid);
            } else {
                log.info("Webhook event ignored: {}", event);
            }
            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            log.error("Error handling webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Webhook processing failed", "details", e.getMessage()));
        }
    }

    // ── RateLimiter fallbacks ─────────────────────────────────────────────────

    public ResponseEntity<?> paymentCreateRateLimitFallback(
            Map<String, Object> body,
            io.github.resilience4j.ratelimiter.RequestNotPermitted ex) {
        log.warn("Rate limit hit on /payments/create");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many payment requests. Please try again shortly."));
    }

    public ResponseEntity<?> paymentVerifyRateLimitFallback(
            Map<String, String> body,
            io.github.resilience4j.ratelimiter.RequestNotPermitted ex) {
        log.warn("Rate limit hit on /payments/verify");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many verification requests. Please try again shortly."));
    }
}
