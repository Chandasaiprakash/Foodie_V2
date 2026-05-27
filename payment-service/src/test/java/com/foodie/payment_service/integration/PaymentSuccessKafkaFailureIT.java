package com.foodie.payment_service.integration;

import com.foodie.payment_service.model.Payment;
import com.foodie.payment_service.repository.PaymentRepository;
import com.foodie.payment_service.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for payment success/failure flows.
 *
 * Kafka publishing is asynchronous.
 * markSuccess() should commit SUCCESS state correctly.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentSuccessKafkaFailureIT extends KafkaIntegrationTestBase {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentService paymentService;

    // -----------------------------------------------------------------------
    // Test 7a: SUCCESS persisted correctly
    // -----------------------------------------------------------------------
    @Test
    void markSuccess_persistsSuccessState() {

        String orderUuid = "kafka-success-" + UUID.randomUUID();

        paymentRepository.save(
            pendingPayment(orderUuid, "success@test.com", 500.0)
        );

        Payment result = paymentService.markSuccess(orderUuid);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");

        List<Payment> storedPayments =
            paymentRepository.findByOrderUuid(orderUuid);

        assertThat(storedPayments).hasSize(1);

        assertThat(storedPayments.get(0).getStatus())
            .isEqualTo("SUCCESS");
    }

    // -----------------------------------------------------------------------
    // Test 7b: FAILED state persisted correctly
    // -----------------------------------------------------------------------
    @Test
    void markFailed_persistsFailedStatusAndReason() {

        String orderUuid = "kafka-failed-" + UUID.randomUUID();

        paymentRepository.save(
            pendingPayment(orderUuid, "failed@test.com", 250.0)
        );

        Payment result =
            paymentService.markFailed(orderUuid, "Card expired");

        assertThat(result.getStatus()).isEqualTo("FAILED");

        assertThat(result.getFailureReason())
            .isEqualTo("Card expired");

        List<Payment> stored =
            paymentRepository.findByOrderUuid(orderUuid);

        assertThat(stored).hasSize(1);

        assertThat(stored.get(0).getStatus())
            .isEqualTo("FAILED");

        assertThat(stored.get(0).getFailureReason())
            .isEqualTo("Card expired");
    }

    // -----------------------------------------------------------------------
    // Test 7c: markSuccess idempotency
    // -----------------------------------------------------------------------
    @Test
    void markSuccess_calledTwice_doesNotCreateDuplicateSuccessState() {

        String orderUuid = "idempotent-" + UUID.randomUUID();

        paymentRepository.save(
            pendingPayment(orderUuid, "idem@test.com", 300.0)
        );

        paymentService.markSuccess(orderUuid);

        paymentService.markSuccess(orderUuid);

        List<Payment> payments =
            paymentRepository.findByOrderUuid(orderUuid);

        assertThat(payments).hasSize(1);

        assertThat(payments.get(0).getStatus())
            .isEqualTo("SUCCESS");
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
