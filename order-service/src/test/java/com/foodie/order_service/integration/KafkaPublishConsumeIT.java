package com.foodie.order_service.integration;

import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.common.events.PaymentFailedEvent;
import com.foodie.order_service.model.Order;
import com.foodie.order_service.model.OrderItem;
import com.foodie.order_service.repository.OrderRepository;
import com.foodie.order_service.service.OrderService;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Integration test 1: Real Kafka publish/consume flow.
 *
 * Verifies that:
 * - OrderService.createOrder() publishes an OrderCreatedEvent to "order-created" topic
 * - A PaymentCompletedEvent consumed from "payment-completed" transitions order to CONFIRMED
 * - A PaymentFailedEvent consumed from "payment-failed" transitions order to CANCELLED
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaPublishConsumeIT extends KafkaIntegrationTestBase {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

    // -----------------------------------------------------------------------
    // Test 1: Order creation publishes event to Kafka
    // -----------------------------------------------------------------------
    @Test
    void createOrder_publishesOrderCreatedEventToKafka() {
        Properties props = consumerProps("verify-create-" + UUID.randomUUID());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("order-created"));

            Order saved = orderService.createOrder(orderRequest(), "pub@test.com");
            assertThat(saved.getOrderUuid()).isNotNull();
            assertThat(saved.getStatus()).isEqualTo("CREATED");

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.count()).isGreaterThan(0);
            });
        }
    }

    // -----------------------------------------------------------------------
    // Test 1b: PaymentCompleted consumed → order CONFIRMED
    // -----------------------------------------------------------------------
    @Test
    void paymentCompletedEvent_confirmsOrderInDatabase() {
        Order saved = createAndPersistOrder("confirm@test.com");

        kafkaTemplate.send("payment-completed", saved.getOrderUuid(),
            PaymentCompletedEvent.builder()
                .orderUuid(saved.getOrderUuid())
                .customerEmail("confirm@test.com")
                .amount(100.0)
                .status("SUCCESS")
                .build());

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CONFIRMED");
            assertThat(updated.getPaymentStatus()).isEqualTo("SUCCESS");
        });
    }

    // -----------------------------------------------------------------------
    // Test 1c: PaymentFailed consumed → order CANCELLED
    // -----------------------------------------------------------------------
    @Test
    void paymentFailedEvent_cancelsOrderInDatabase() {
        Order saved = createAndPersistOrder("fail@test.com");

        kafkaTemplate.send("payment-failed", saved.getOrderUuid(),
            PaymentFailedEvent.builder()
                .orderUuid(saved.getOrderUuid())
                .customerEmail("fail@test.com")
                .amount(100.0)
                .reason("Insufficient funds")
                .build());

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findByOrderUuid(saved.getOrderUuid()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CANCELLED");
            assertThat(updated.getPaymentStatus()).isEqualTo("FAILED");
        });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private Order createAndPersistOrder(String email) {
        Order o = new Order();
        o.setOrderUuid(UUID.randomUUID().toString());
        o.setCustomerEmail(email);
        o.setStatus("CREATED");
        o.setPaymentStatus("PENDING");
        o.setRestaurantId("r1");
        o.setRestaurantName("Test Restaurant");
        o.setCustomerPhone("9999999999");
        o.setItems(List.of(new OrderItem("Burger", 1, 100.0)));
        o.setTotal(100.0);
        o.setCreatedAt(java.time.Instant.now());
        return orderRepository.save(o);
    }

    private Order orderRequest() {
        Order o = new Order();
        o.setRestaurantId("r1");
        o.setRestaurantName("Test Restaurant");
        o.setCustomerPhone("9999999999");
        o.setItems(List.of(new OrderItem("Burger", 1, 100.0)));
        return o;
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
