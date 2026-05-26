package com.foodie.order_service.integration;

import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.order_service.repository.OrderRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

/**
 * Integration test 2: DLQ routing.
 *
 * Sends a PaymentCompletedEvent for an order UUID that does not exist in the DB.
 * The listener throws IllegalStateException → @RetryableTopic retries 4 times
 * → exhausted → message lands on payment-completed-dlt.
 *
 * Verifies the DLT topic receives the message after retry exhaustion.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DlqRoutingIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private OrderRepository orderRepository;

    @Test
    void orphanPaymentCompleted_exhaustsRetriesAndLandsOnDlt() throws Exception {
        String orphanUuid = "no-such-order-" + UUID.randomUUID();

        Map<String, Object> props = dltConsumerProps("dlt-verify-" + UUID.randomUUID());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("payment-completed-dlt"));

            kafkaTemplate.send("payment-completed", orphanUuid,
                PaymentCompletedEvent.builder()
                    .orderUuid(orphanUuid)
                    .customerEmail("ghost@test.com")
                    .amount(50.0)
                    .status("SUCCESS")
                    .build());

            // RetryableTopic: 4 attempts with 1s/2s/4s backoff ≈ 7 seconds total
            // Add headroom → 60s timeout
            List<String> dltKeys = new ArrayList<>();
            await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> r : records) {
                    dltKeys.add(r.key());
                }
                assertThat(dltKeys).anyMatch(k -> k != null && k.equals(orphanUuid));
            });
        }

        // Verify no order was accidentally created
        assertThat(orderRepository.findByOrderUuid(orphanUuid)).isEmpty();
    }

    private Map<String, Object> dltConsumerProps(String bootstrapServers) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(bootstrapServers, "order-service-dlt-group", "false");
        // If your project uses specific deserializers, configure them here:
        // props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}
