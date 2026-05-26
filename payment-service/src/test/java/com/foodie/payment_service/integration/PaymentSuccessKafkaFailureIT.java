package com.foodie.payment_service.integration;

import com.foodie.payment_service.model.Payment;
import com.foodie.payment_service.repository.PaymentRepository;
import com.foodie.payment_service.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Integration test 7: Payment success + Kafka publish failure → DB rollback.
 * Integration test 7b: Payment success + Kafka OK → DB persists SUCCESS.
 *
 * markSuccess() is @Transactional. When Kafka.send() throws, the DB transaction
 * must roll back — payment stays PENDING. This validates that:
 *   1. The transactional boundary covers both DB write and Kafka publish
 *   2. Failure atomicity: no partial success (DB written but Kafka failed)
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentSuccessKafkaFailureIT extends KafkaIntegrationTestBase {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentService paymentService;
    @SpyBean  private KafkaTemplate<String, Object> kafkaTemplate;

    // -----------------------------------------------------------------------
    // Test 7a: Kafka publish fails → DB stays PENDING (rollback)
    // -----------------------------------------------------------------------
    @Test
    void markSuccess_kafkaPublishFails_dbRolledBack() {
        String orderUuid = "kafka-fail-" + UUID.randomUUID();
        paymentRepository.save(pendingPayment(orderUuid, "rollback@test.com", 500.0));

        // Force Kafka.send() to throw a runtime exception
        doThrow(new RuntimeException("Simulated Kafka broker outage"))
            .when(kafkaTemplate).send(anyString(), anyString(), any());

        // markSuccess should propagate the Kafka exception
        assertThatThrownBy(() -> paymentService.markSuccess(orderUuid))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Simulated Kafka broker outage");

        // DB must still show PENDING — transaction was rolled back
        List<Payment> payments = paymentRepository.findByOrderUuid(orderUuid);
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus())
            .as("DB must stay PENDING when Kafka publish fails and transaction rolls back")
            .isEqualTo("PENDING");
    }

    // -----------------------------------------------------------------------
    // Test 7b: Kafka OK → DB shows SUCCESS
    // -----------------------------------------------------------------------
    @Test
    void markSuccess_kafkaOk_dbShowsSuccess() {
        String orderUuid = "kafka-ok-" + UUID.randomUUID();
        paymentRepository.save(pendingPayment(orderUuid, "ok@test.com", 100.0));

        Payment result = paymentService.markSuccess(orderUuid);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        List<Payment> stored = paymentRepository.findByOrderUuid(orderUuid);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getStatus()).isEqualTo("SUCCESS");
    }

    // -----------------------------------------------------------------------
    // Test 7c: markFailed → DB shows FAILED + event published
    // -----------------------------------------------------------------------
    @Test
    void markFailed_persistsFailedStatusAndReason() {
        String orderUuid = "kafka-failed-" + UUID.randomUUID();
        paymentRepository.save(pendingPayment(orderUuid, "failed@test.com", 250.0));

        Payment result = paymentService.markFailed(orderUuid, "Card expired");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getFailureReason()).isEqualTo("Card expired");

        List<Payment> stored = paymentRepository.findByOrderUuid(orderUuid);
        assertThat(stored.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(stored.get(0).getFailureReason()).isEqualTo("Card expired");
    }

    // -----------------------------------------------------------------------
    private Payment pendingPayment(String orderUuid, String email, double amount) {
        return Payment.builder()
            .orderUuid(orderUuid)
            .customerEmail(email)
            .amount(amount)
            .method("ONLINE")
            .status("PENDING")
            .createdAt(Instant.now())
            .paymentUuid(UUID.randomUUID().toString())
            .build();
    }
}
