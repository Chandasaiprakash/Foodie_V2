package com.foodie.order_service.integration;

import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.order_service.model.Order;
import com.foodie.order_service.model.OrderItem;
import com.foodie.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test 5: Consumer retry handling.
 *
 * Scenario: a PaymentCompletedEvent arrives BEFORE the order is saved to the DB
 * (race condition between order-service and payment-service startup).
 * The listener throws → retries → by the time of the 2nd retry the order exists.
 *
 * We simulate this by sending the event first, then persisting the order
 * within the retry window.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RetryHandlingIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderRepository orderRepository;

    @Test
    void paymentCompleted_arrivesBeforeOrder_eventuallyProcessedAfterRetry() throws Exception {
        String orderUuid = "late-order-" + UUID.randomUUID();

        // Send event BEFORE order exists in DB
        kafkaTemplate.send("payment-completed", orderUuid,
            PaymentCompletedEvent.builder()
                .orderUuid(orderUuid)
                .customerEmail("retry@test.com")
                .amount(200.0)
                .status("SUCCESS")
                .build());

        // Create order 3 seconds later (within retry window)
        Thread.sleep(3000);
        Order o = new Order();
        o.setOrderUuid(orderUuid);
        o.setCustomerEmail("retry@test.com");
        o.setStatus("CREATED");
        o.setPaymentStatus("PENDING");
        o.setRestaurantId("r1");
        o.setRestaurantName("Retry Test");
        o.setCustomerPhone("1234567890");
        o.setItems(List.of(new OrderItem("Pizza", 1, 200.0)));
        o.setTotal(200.0);
        o.setCreatedAt(Instant.now());
        orderRepository.saveAndFlush(o);

        // Retry should pick it up and confirm
        await()
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(90))
            .untilAsserted(() -> {
                Order updated = orderRepository.findByOrderUuid(orderUuid).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
            });
    }
}
