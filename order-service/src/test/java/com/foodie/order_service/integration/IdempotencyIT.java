package com.foodie.order_service.integration;

import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.common.events.DeliveryEvent;
import com.foodie.order_service.idempotency.ProcessedEventRepository;
import com.foodie.order_service.model.Order;
import com.foodie.order_service.model.OrderItem;
import com.foodie.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Comprehensive idempotency integration tests.
 *
 * Tests covered:
 *   3.  Sequential duplicate PaymentCompleted — processed exactly once
 *   4.  Concurrent duplicate PaymentCompleted (5 threads) — processed exactly once
 *   5.  DeliveryEvent idempotency — same status duplicate suppressed
 *   6.  DeliveryEvent legitimate transitions — different statuses all processed
 *   9.  DLQ replay — already-processed event not re-applied
 *   10. TTL field presence — expires_at is set correctly on claim
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdempotencyIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void cleanIdempotencyTable() {
        processedEventRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // Test 3: Sequential duplicate suppression
    // -----------------------------------------------------------------------
    @Test
    void duplicatePaymentCompleted_processedExactlyOnce() throws Exception {
        Order saved = savedOrder("dup-seq@test.com");
        PaymentCompletedEvent event = paymentSuccess(saved.getOrderUuid());

        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);
        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event); // duplicate

        await().atMost(25, SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        });

        String eventId = "payment-completed::" + saved.getOrderUuid();
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(processedEventRepository.findAll())
            .filteredOn(pe -> pe.getEventId().equals(eventId))
            .hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Test 4: Concurrent duplicate — exactly-once under race conditions
    // -----------------------------------------------------------------------
    @Test
    void concurrentDuplicatePaymentCompleted_processedExactlyOnce() throws Exception {
        Order saved = savedOrder("dup-concurrent@test.com");
        PaymentCompletedEvent event = paymentSuccess(saved.getOrderUuid());

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go    = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        executor.shutdown();
        executor.awaitTermination(10, SECONDS);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        });

        String eventId = "payment-completed::" + saved.getOrderUuid();
        long count = processedEventRepository.findAll().stream()
            .filter(pe -> pe.getEventId().equals(eventId))
            .count();
        assertThat(count).isEqualTo(1L);
    }

    // -----------------------------------------------------------------------
    // Test 5: DeliveryEvent — same status duplicate suppressed
    // -----------------------------------------------------------------------
    @Test
    void duplicateDeliveryEvent_sameStatus_suppressedAfterFirst() throws Exception {
        Order saved = savedOrder("delivery-dup@test.com");
        DeliveryEvent event = deliveryEvent(saved.getOrderUuid(), "ASSIGNED");

        kafkaTemplate.send("delivery-events", saved.getOrderUuid(), event);
        kafkaTemplate.send("delivery-events", saved.getOrderUuid(), event); // duplicate

        await().atMost(25, SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("ASSIGNED");
        });

        String eventId = "delivery-event::" + saved.getOrderUuid() + "::ASSIGNED";
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(processedEventRepository.findAll())
            .filteredOn(pe -> pe.getEventId().equals(eventId))
            .hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Test 6: DeliveryEvent — legitimate state transitions all processed
    // -----------------------------------------------------------------------
    @Test
    void deliveryEvents_differentStatuses_allProcessed() throws Exception {
        Order saved = savedOrder("delivery-transitions@test.com");

        kafkaTemplate.send("delivery-events", saved.getOrderUuid(),
                deliveryEvent(saved.getOrderUuid(), "ASSIGNED"));

        await().atMost(20, SECONDS).untilAsserted(() ->
            assertThat(orderRepository.findByOrderUuid(saved.getOrderUuid())
                .map(Order::getStatus).orElse("")).isEqualTo("ASSIGNED"));

        kafkaTemplate.send("delivery-events", saved.getOrderUuid(),
                deliveryEvent(saved.getOrderUuid(), "DELIVERED"));

        await().atMost(20, SECONDS).untilAsserted(() ->
            assertThat(orderRepository.findByOrderUuid(saved.getOrderUuid())
                .map(Order::getStatus).orElse("")).isEqualTo("DELIVERED"));

        // Both idempotency records present — different eventIds
        assertThat(processedEventRepository.existsById(
            "delivery-event::" + saved.getOrderUuid() + "::ASSIGNED")).isTrue();
        assertThat(processedEventRepository.existsById(
            "delivery-event::" + saved.getOrderUuid() + "::DELIVERED")).isTrue();
    }

    // -----------------------------------------------------------------------
    // Test 9: DLQ replay — already-processed event not re-applied
    // -----------------------------------------------------------------------
    @Test
    void dlqReplay_alreadyProcessedEvent_isIdempotentlySkipped() throws Exception {
        Order saved = savedOrder("dlq-replay@test.com");
        PaymentCompletedEvent event = paymentSuccess(saved.getOrderUuid());

        // First delivery — process normally
        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);
        await().atMost(25, SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        });

        // Manually reset to detect if replay re-processes
        orderRepository.findByOrderUuid(saved.getOrderUuid()).ifPresent(o -> {
            o.setStatus("SENTINEL_VALUE");
            orderRepository.save(o);
        });

        // DLQ replay — same event again
        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);

        Thread.sleep(5_000);
        Order afterReplay = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
        assertThat(afterReplay.getStatus())
            .as("DLQ replay must not change the order status")
            .isEqualTo("SENTINEL_VALUE");
    }

    // -----------------------------------------------------------------------
    // Test 10: TTL field is set on processed event records
    // -----------------------------------------------------------------------
    @Test
    void processedEvent_hasExpiresAt_setToFuture() throws Exception {
        Order saved = savedOrder("ttl-check@test.com");
        PaymentCompletedEvent event = paymentSuccess(saved.getOrderUuid());

        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);

        String eventId = "payment-completed::" + saved.getOrderUuid();
        await().atMost(25, SECONDS).untilAsserted(() ->
            assertThat(processedEventRepository.existsById(eventId)).isTrue());

        processedEventRepository.findById(eventId).ifPresent(pe -> {
            assertThat(pe.getExpiresAt())
                .as("expires_at must be in the future (at least 6 days from now)")
                .isAfter(Instant.now().plusSeconds(6 * 86_400));
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private Order savedOrder(String email) {
        Order o = new Order();
        o.setOrderUuid(UUID.randomUUID().toString());
        o.setCustomerEmail(email);
        o.setStatus("CREATED");
        o.setPaymentStatus("PENDING");
        o.setRestaurantId("r1");
        o.setRestaurantName("Test Restaurant");
        o.setCustomerPhone("0000000000");
        o.setItems(List.of(new OrderItem("Burger", 1, 150.0)));
        o.setTotal(150.0);
        o.setCreatedAt(Instant.now());
        return orderRepository.save(o);
    }

    private PaymentCompletedEvent paymentSuccess(String orderUuid) {
        return PaymentCompletedEvent.builder()
            .orderUuid(orderUuid)
            .customerEmail("test@test.com")
            .amount(150.0)
            .status("SUCCESS")
            .build();
    }

    private DeliveryEvent deliveryEvent(String orderUuid, String status) {
        return DeliveryEvent.builder()
            .orderUuid(orderUuid)
            .status(status)
            .customerEmail("test@test.com")
            .timestamp(System.currentTimeMillis())
            .build();
    }
}
