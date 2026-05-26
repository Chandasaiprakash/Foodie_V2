package com.foodie.order_service.integration;

import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.order_service.idempotency.ProcessedEventRepository;
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
 * Integration test 8: Consumer DB commit success + ACK failure simulation.
 *
 * Kafka's at-least-once delivery means: if DB commits but ACK to broker
 * fails (network blip, consumer restart), the message is re-delivered.
 * The idempotency guard (processed_events unique constraint) must prevent
 * double-processing on re-delivery.
 *
 * We simulate this by:
 * 1. Processing an event (DB committed, idempotency claimed)
 * 2. Sending the same event again (simulating re-delivery after missed ACK)
 * 3. Asserting the second delivery is suppressed
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DbCommitAckFailureIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;

    @Test
    void redeliveredEvent_afterDbCommit_suppressedByIdempotency() throws Exception {
        Order saved = persistedOrder("ack-fail@test.com");
        String orderUuid = saved.getOrderUuid();

        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
            .orderUuid(orderUuid)
            .customerEmail("ack-fail@test.com")
            .amount(150.0)
            .status("SUCCESS")
            .build();

        // First delivery — processes successfully
        kafkaTemplate.send("payment-completed", orderUuid, event);
        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(orderUuid).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        });

        // Simulate ACK failure: manually reset order status to detect re-processing
        orderRepository.findByOrderUuid(orderUuid).ifPresent(o -> {
            o.setStatus("RESET_SENTINEL");
            orderRepository.save(o);
        });

        // Re-delivery (simulating broker re-sending after missed ACK)
        kafkaTemplate.send("payment-completed", orderUuid, event);

        // Wait — if idempotency works, sentinel value persists
        Thread.sleep(6000);

        Order afterRedelivery = orderRepository.findByOrderUuid(orderUuid).orElseThrow();
        // Status must still be RESET_SENTINEL — idempotency blocked re-processing
        assertThat(afterRedelivery.getStatus())
            .as("Re-delivered event must be suppressed by idempotency guard")
            .isEqualTo("RESET_SENTINEL");

        // Exactly one idempotency record
        String eventId = "payment-completed::" + orderUuid;
        long count = processedEventRepository.findAll().stream()
            .filter(pe -> pe.getEventId().equals(eventId))
            .count();
        assertThat(count).isEqualTo(1L);
    }

    private Order persistedOrder(String email) {
        Order o = new Order();
        o.setOrderUuid(UUID.randomUUID().toString());
        o.setCustomerEmail(email);
        o.setStatus("CREATED");
        o.setPaymentStatus("PENDING");
        o.setRestaurantId("r1");
        o.setRestaurantName("ACK Test");
        o.setCustomerPhone("1111111111");
        o.setItems(List.of(new OrderItem("Wrap", 1, 150.0)));
        o.setTotal(150.0);
        o.setCreatedAt(Instant.now());
        return orderRepository.save(o);
    }
}
