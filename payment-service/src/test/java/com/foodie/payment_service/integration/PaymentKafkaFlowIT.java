package com.foodie.payment_service.integration;

import com.foodie.common.events.OrderCreatedEvent;
import com.foodie.payment_service.idempotency.ProcessedEventRepository;
import com.foodie.payment_service.model.Payment;
import com.foodie.payment_service.repository.PaymentRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for payment-service Kafka flows.
 *
 * Test 1: OrderCreated → payment record created (PENDING)
 * Test 3: Duplicate OrderCreated → processed exactly once
 * Test 4: Concurrent duplicate → exactly once
 * Test 7: payment-completed topic receives event after markSuccess
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentKafkaFlowIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ProcessedEventRepository processedEventRepository;
    @Autowired private com.foodie.payment_service.service.PaymentService paymentService;

    // -----------------------------------------------------------------------
    // Test 1: OrderCreated → PENDING payment record
    // -----------------------------------------------------------------------
    @Test
    void orderCreatedEvent_createsPendingPaymentRecord() {
        String orderUuid = "ord-" + UUID.randomUUID();
        kafkaTemplate.send("order-created", orderUuid, orderCreatedEvent(orderUuid));

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Payment> payments = paymentRepository.findByOrderUuid(orderUuid);
            assertThat(payments).hasSize(1);
            assertThat(payments.get(0).getStatus()).isEqualTo("PENDING");
        });
    }

    // -----------------------------------------------------------------------
    // Test 3: Duplicate OrderCreated → exactly one payment
    // -----------------------------------------------------------------------
    @Test
    void duplicateOrderCreated_createsOnlyOnePaymentRecord() {
        String orderUuid = "ord-dup-" + UUID.randomUUID();
        OrderCreatedEvent event = orderCreatedEvent(orderUuid);

        kafkaTemplate.send("order-created", orderUuid, event);
        kafkaTemplate.send("order-created", orderUuid, event); // duplicate

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Payment> payments = paymentRepository.findByOrderUuid(orderUuid);
            assertThat(payments).hasSize(1);
        });

        String eventId = "order-created::" + orderUuid;
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
        long count = processedEventRepository.findAll().stream()
            .filter(pe -> pe.getEventId().equals(eventId)).count();
        assertThat(count).isEqualTo(1L);
    }

    // -----------------------------------------------------------------------
    // Test 4: Concurrent duplicate → exactly one payment
    // -----------------------------------------------------------------------
    @Test
    void concurrentDuplicateOrderCreated_createsOnlyOnePaymentRecord() throws Exception {
        String orderUuid = "ord-conc-" + UUID.randomUUID();
        OrderCreatedEvent event = orderCreatedEvent(orderUuid);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    kafkaTemplate.send("order-created", orderUuid, event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            List<Payment> payments = paymentRepository.findByOrderUuid(orderUuid);
            assertThat(payments).hasSize(1);
        });
    }

    // -----------------------------------------------------------------------
    // Test 7: markSuccess → payment-completed topic receives event
    // -----------------------------------------------------------------------
    @Test
    void markSuccess_publishesPaymentCompletedToKafkaTopic() throws Exception {
        String orderUuid = "ord-success-" + UUID.randomUUID();
        Payment payment = Payment.builder()
            .orderUuid(orderUuid)
            .customerEmail("success@test.com")
            .amount(299.0)
            .method("ONLINE")
            .status("PENDING")
            .createdAt(Instant.now())
            .paymentUuid(UUID.randomUUID().toString())
            .build();
        paymentRepository.save(payment);

        Properties props = consumerProps("success-verify-" + UUID.randomUUID());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("payment-completed"));

            // markSuccess is transactional: persists SUCCESS + publishes event
            paymentService.markSuccess(orderUuid);

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.count()).isGreaterThan(0);
            });
        }

        // DB must reflect SUCCESS
        Payment saved = paymentRepository.findByOrderUuid(orderUuid).get(0);
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
    }

    // -----------------------------------------------------------------------
    private OrderCreatedEvent orderCreatedEvent(String orderUuid) {
        return OrderCreatedEvent.builder()
            .orderUuid(orderUuid)
            .customerEmail("payer@test.com")
            .customerPhone("9999999999")
            .restaurantId("r1")
            .restaurantName("Test Restaurant")
            .items(List.of(new OrderCreatedEvent.OrderItemDto("Burger", 1, 150.0)))
            .total(150.0)
            .build();
    }

    private Properties consumerProps(String groupId) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return p;
    }
}
