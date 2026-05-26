package com.foodie.order_service.integration;

import com.foodie.common.events.PaymentFailedEvent;
import com.foodie.order_service.model.Order;
import com.foodie.order_service.model.OrderItem;
import com.foodie.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Integration test 10: Saga compensation retry behavior.
 *
 * Scenario A: PaymentFailedEvent arrives → order is compensated (CANCELLED).
 * Scenario B: Compensation for an already-compensated order is idempotent
 *             (the order stays CANCELLED, no additional state changes).
 * Scenario C: PaymentFailedEvent arrives before order → retry → compensation
 *             eventually succeeds.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SagaCompensationIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderRepository orderRepository;

    // -----------------------------------------------------------------------
    // Scenario A: Normal compensation
    // -----------------------------------------------------------------------
    @Test
    void paymentFailed_compensatesOrderToCancelled() {
        Order saved = persistedOrder("saga-a@test.com");

        kafkaTemplate.send("payment-failed", saved.getOrderUuid(),
            PaymentFailedEvent.builder()
                .orderUuid(saved.getOrderUuid())
                .customerEmail("saga-a@test.com")
                .amount(100.0)
                .reason("Card declined")
                .build());

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CANCELLED");
            assertThat(updated.getPaymentStatus()).isEqualTo("FAILED");
        });
    }

    // -----------------------------------------------------------------------
    // Scenario B: Duplicate compensation is idempotent
    // -----------------------------------------------------------------------
    @Test
    void duplicatePaymentFailed_compensationIsIdempotent() throws Exception {
        Order saved = persistedOrder("saga-b@test.com");

        PaymentFailedEvent event = PaymentFailedEvent.builder()
            .orderUuid(saved.getOrderUuid())
            .customerEmail("saga-b@test.com")
            .amount(100.0)
            .reason("Timeout")
            .build();

        kafkaTemplate.send("payment-failed", saved.getOrderUuid(), event);

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CANCELLED");
        });

        // Send duplicate — should be idempotently skipped
        kafkaTemplate.send("payment-failed", saved.getOrderUuid(), event);
        Thread.sleep(5000);

        // Status should remain CANCELLED, not double-cancelled or error state
        Order afterDuplicate = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
        assertThat(afterDuplicate.getStatus()).isEqualTo("CANCELLED");
    }

    // -----------------------------------------------------------------------
    // Scenario C: Compensation retry — order arrives late
    // -----------------------------------------------------------------------
    @Test
    void paymentFailed_arrivesBeforeOrder_compensationEventuallySucceeds() throws Exception {
        String orderUuid = "late-comp-" + UUID.randomUUID();

        kafkaTemplate.send("payment-failed", orderUuid,
            PaymentFailedEvent.builder()
                .orderUuid(orderUuid)
                .customerEmail("saga-c@test.com")
                .amount(100.0)
                .reason("Bank error")
                .build());

        // Persist order after small delay (within retry window)
        Thread.sleep(2500);
        Order o = new Order();
        o.setOrderUuid(orderUuid);
        o.setCustomerEmail("saga-c@test.com");
        o.setStatus("CREATED");
        o.setPaymentStatus("PENDING");
        o.setRestaurantId("r1");
        o.setRestaurantName("Late Order");
        o.setCustomerPhone("0000000000");
        o.setItems(List.of(new OrderItem("Biryani", 2, 150.0)));
        o.setTotal(300.0);
        o.setCreatedAt(Instant.now());
        orderRepository.save(o);

        // Retry must eventually cancel it
        await().atMost(40, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(orderUuid).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CANCELLED");
        });
    }

    // -----------------------------------------------------------------------
    private Order persistedOrder(String email) {
        Order o = new Order();
        o.setOrderUuid(UUID.randomUUID().toString());
        o.setCustomerEmail(email);
        o.setStatus("CREATED");
        o.setPaymentStatus("PENDING");
        o.setRestaurantId("r1");
        o.setRestaurantName("Saga Test");
        o.setCustomerPhone("9876543210");
        o.setItems(List.of(new OrderItem("Dosa", 1, 100.0)));
        o.setTotal(100.0);
        o.setCreatedAt(Instant.now());
        return orderRepository.save(o);
    }
}
