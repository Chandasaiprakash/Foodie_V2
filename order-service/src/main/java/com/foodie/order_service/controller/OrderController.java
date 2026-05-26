package com.foodie.order_service.controller;

import com.foodie.order_service.model.Order;
import com.foodie.order_service.service.OrderService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * All writes go through OrderService — no repository access in the controller.
 * This ensures every create/update publishes the correct Kafka events and
 * runs inside a proper @Transactional boundary.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    @PostMapping
    @Bulkhead(name = "orderCreation", fallbackMethod = "placeOrderFallback")
    public ResponseEntity<Order> placeOrder(
            @Valid @RequestBody Order order,
            @RequestHeader("X-User-Email") String customerEmail) {
        Order saved = orderService.createOrder(order, customerEmail);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/id/{id}")
    public ResponseEntity<Order> getById(
            @PathVariable Long id,
            @RequestHeader("X-User-Email") String customerEmail) {
        return orderService.getByIdIfOwned(id, customerEmail)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(403).build());
    }

    @GetMapping("/{orderUuid}")
    public ResponseEntity<Order> getByUuid(
            @PathVariable String orderUuid,
            @RequestHeader("X-User-Email") String customerEmail) {
        return orderService.getByUuidIfOwned(orderUuid, customerEmail)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(403).build());
    }

    @GetMapping("customer/{email}")
    public ResponseEntity<List<Order>> getByCustomer(
            @PathVariable String email,
            @RequestHeader("X-User-Email") String customerEmail) {
        if (!email.equalsIgnoreCase(customerEmail)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(orderService.getByCustomerEmail(email));
    }

    /**
     * Internal-only endpoint called by payment-service via Feign.
     * Delegates to OrderService so the update stays transactional.
     */
    @PutMapping("/{orderUuid}/payment-status")
    public ResponseEntity<String> updatePaymentStatus(
            @PathVariable String orderUuid,
            @RequestParam String status) {
        orderService.updatePaymentStatus(orderUuid, status);
        return ResponseEntity.ok("Payment status updated to " + status);
    }

    @DeleteMapping("/{orderUuid}")
    public ResponseEntity<?> deleteOrder(@PathVariable String orderUuid) {
        boolean deleted = orderService.deleteOrder(orderUuid);
        return deleted
                ? ResponseEntity.ok(Map.of("message", "Order deleted successfully"))
                : ResponseEntity.status(404).body(Map.of("error", "Order not found"));
    }

    /**
     * Reorder — routes through OrderService.createOrder() so the
     * OrderCreatedEvent is published to Kafka and the payment + delivery
     * saga starts correctly.
     */
    @PostMapping("/reorder")
    @Bulkhead(name = "orderCreation", fallbackMethod = "placeOrderFallback")
    public ResponseEntity<Order> reorder(
            @RequestBody Order originalOrder,
            @RequestHeader("X-User-Email") String customerEmail) {
        log.info("Reorder requested by {} for restaurant {}",
                 customerEmail, originalOrder.getRestaurantName());
        Order saved = orderService.createOrder(originalOrder, customerEmail);
        return ResponseEntity.ok(saved);
    }

    // ── Resilience fallbacks ──────────────────────────────────────────────────

    /**
     * Bulkhead fallback for placeOrder / reorder.
     * Fires when more than {@code max-concurrent-calls} order-creation requests
     * are in-flight simultaneously, protecting the service under traffic spikes.
     */
    public ResponseEntity<Order> placeOrderFallback(
            Order order, String customerEmail,
            io.github.resilience4j.bulkhead.BulkheadFullException ex) {
        log.warn("Bulkhead full for orderCreation — rejecting request from {}", customerEmail);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }

    @GetMapping("/customer/{email}/paged")
    public ResponseEntity<Map<String, Object>> getPagedOrders(
            @PathVariable String email,
            @RequestHeader("X-User-Email") String customerEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String search) {
        if (!email.equalsIgnoreCase(customerEmail)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(orderService.getPagedOrders(email, page, size, sort, search));
    }
}
