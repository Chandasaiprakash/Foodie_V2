package com.foodie.gateway_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Gateway fallback controller.
 *
 * Invoked by the Spring Cloud Gateway CircuitBreaker filter when a downstream
 * service's circuit is OPEN or the request times out.  Returns a structured
 * 503 response so clients (and the frontend) can show a meaningful message
 * instead of an opaque connection-refused error.
 *
 * Each critical service gets its own fallback path so the circuit breaker
 * name is clearly logged and the message is domain-specific.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // ── Payment service fallback ─────────────────────────────────────────────
    @RequestMapping("/payment")
    public Mono<ResponseEntity<Map<String, Object>>> paymentFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody(
                        "payment-service",
                        "Payment service is currently unavailable. " +
                        "Please try again in a moment. No charge has been made."
                )));
    }

    // ── Order service fallback ───────────────────────────────────────────────
    @RequestMapping("/order")
    public Mono<ResponseEntity<Map<String, Object>>> orderFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody(
                        "order-service",
                        "Order service is temporarily unavailable. " +
                        "Your order has not been placed. Please retry shortly."
                )));
    }

    // ── Delivery service fallback ────────────────────────────────────────────
    @RequestMapping("/delivery")
    public Mono<ResponseEntity<Map<String, Object>>> deliveryFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody(
                        "delivery-service",
                        "Delivery service is temporarily unavailable. " +
                        "Delivery tracking may be delayed."
                )));
    }

    // ── Generic fallback (catch-all) ─────────────────────────────────────────
    @RequestMapping("/generic")
    public Mono<ResponseEntity<Map<String, Object>>> genericFallback() {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody(
                        "unknown",
                        "The requested service is temporarily unavailable. Please retry."
                )));
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private Map<String, Object> fallbackBody(String service, String message) {
        return Map.of(
                "status", 503,
                "error", "Service Unavailable",
                "service", service,
                "message", message,
                "timestamp", Instant.now().toString(),
                "circuitBreakerOpen", true
        );
    }
}
