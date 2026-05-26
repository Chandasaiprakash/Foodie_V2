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
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests 3 & 4: Duplicate and concurrent duplicate event handling.
 *
 * Test 3: Sequential duplicate — same event sent twice → processed exactly once.
 * Test 4: Concurrent duplicate — same event sent from N threads simultaneously
 *         → exactly-once processing with no double-state-mutations.
 * Test 9: DLQ replay idempotency — replaying a DLT message does not re-process.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DuplicateEventIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;

    // -----------------------------------------------------------------------
    // Test 3: Sequential duplicate suppression
    // -----------------------------------------------------------------------
    @Test
    void duplicatePaymentCompleted_processedExactlyOnce() throws Exception {
        Order saved = savedOrder("dup-seq@test.com");

        PaymentCompletedEvent event = successEvent(saved.getOrderUuid());
        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);
        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event); // duplicate

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        });

        // Idempotency record exists exactly once
        String eventId = "payment-completed::" + saved.getOrderUuid();
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        assertThat(processedEventRepository.findAll())
            .filteredOn(pe -> pe.getEventId().equals(eventId))
            .hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Test 4: Concurrent duplicate protection
    // -----------------------------------------------------------------------
    @Test
    void concurrentDuplicatePaymentCompleted_processedExactlyOnce() throws Exception {
        Order saved = savedOrder("dup-concurrent@test.com");

        PaymentCompletedEvent event = successEvent(saved.getOrderUuid());

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        // Fire all 5 sends simultaneously
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
        executor.awaitTermination(10, TimeUnit.SECONDS);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        });

        // Exactly one idempotency record despite 5 concurrent sends
        String eventId = "payment-completed::" + saved.getOrderUuid();
        long count = processedEventRepository.findAll().stream()
            .filter(pe -> pe.getEventId().equals(eventId))
            .count();
        assertThat(count).isEqualTo(1L);
    }

    // -----------------------------------------------------------------------
    // Test 9: DLQ replay idempotency
    // Simulates: an event was already processed, then replayed from DLT.
    // The replay must be suppressed without side effects.
    // -----------------------------------------------------------------------
    @Test
    void dlqReplay_alreadyProcessedEvent_isIdempotentlySkipped() throws Exception {
        Order saved = savedOrder("dlq-replay@test.com");

        PaymentCompletedEvent event = successEvent(saved.getOrderUuid());

        // First delivery — process normally
        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);
        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
        });

        // Manually reset order to CREATED to detect if replay causes re-processing
        orderRepository.findByOrderUuid(saved.getOrderUuid()).ifPresent(o -> {
            o.setStatus("CREATED_RESET_FOR_TEST");
            orderRepository.save(o);
        });

        // DLQ replay — same event again
        kafkaTemplate.send("payment-completed", saved.getOrderUuid(), event);

        // Wait and assert status was NOT changed by the replay
        Thread.sleep(5000);
        Order afterReplay = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
        assertThat(afterReplay.getStatus()).isEqualTo("CREATED_RESET_FOR_TEST");
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
        o.setRestaurantName("Test");
        o.setCustomerPhone("0000000000");
        o.setItems(List.of(new OrderItem("Item", 1, 100.0)));
        o.setTotal(100.0);
        o.setCreatedAt(Instant.now());
        return orderRepository.save(o);
    }

    private PaymentCompletedEvent successEvent(String orderUuid) {
        return PaymentCompletedEvent.builder()
            .orderUuid(orderUuid)
            .customerEmail("test@test.com")
            .amount(100.0)
            .status("SUCCESS")
            .build();
    }
}
