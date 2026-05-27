package com.foodie.payment_service.service;

import com.foodie.common.events.OrderCreatedEvent;
import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.common.events.PaymentFailedEvent;
import com.foodie.payment_service.model.Payment;
import com.foodie.payment_service.outbox.OutboxEventService;
import com.foodie.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxEventService outboxEventService;

    private static final String TOPIC_PAYMENT_SUCCESS = "payment-completed";
    private static final String TOPIC_PAYMENT_FAILED  = "payment-failed";

    /**
     * Kafka replay-safe payment creation.
     *
     * Duplicate Kafka delivery/replay/rebalance storms must never create
     * multiple payment rows for the same order.
     */
    @Transactional
    public Payment processPayment(OrderCreatedEvent orderEvent) {

        List<Payment> existing =
                paymentRepository.findByOrderUuid(orderEvent.getOrderUuid());

        if (!existing.isEmpty()) {

            log.info(
                    "Duplicate payment replay ignored for order {}",
                    orderEvent.getOrderUuid()
            );

            return existing.get(0);
        }

        Payment payment = Payment.builder()
                .orderUuid(orderEvent.getOrderUuid())
                .customerEmail(orderEvent.getCustomerEmail())
                .amount(orderEvent.getTotal())
                .method("ONLINE")
                .status("PENDING")
                .createdAt(Instant.now())
                .paymentUuid(UUID.randomUUID().toString())
                .build();

        try {

            Payment saved = paymentRepository.saveAndFlush(payment);

            log.info(
                    "Payment record created for order {} status={}",
                    saved.getOrderUuid(),
                    saved.getStatus()
            );

            return saved;

        } catch (DataIntegrityViolationException ex) {

            log.warn(
                    "Concurrent duplicate payment suppressed for order {}",
                    orderEvent.getOrderUuid()
            );

            return paymentRepository
                    .findByOrderUuid(orderEvent.getOrderUuid())
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional
    public Payment createPendingForOrder(String orderUuid, String customerEmail, double amount) {
        List<Payment> existing = paymentRepository.findByOrderUuid(orderUuid);

        if (!existing.isEmpty()) {
            log.info(
                    "Payment already exists for order {} — returning existing record (status={})",
                    orderUuid,
                    existing.get(0).getStatus()
            );

            return existing.get(0);
        }

        Payment payment = Payment.builder()
                .orderUuid(orderUuid)
                .customerEmail(customerEmail)
                .amount(amount)
                .method("ONLINE")
                .status("PENDING")
                .createdAt(Instant.now())
                .paymentUuid(UUID.randomUUID().toString())
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("Created pending payment for order {}", orderUuid);

        return saved;
    }

    @Transactional
    public Payment markSuccess(String orderUuid) {
        List<Payment> list = paymentRepository.findByOrderUuid(orderUuid);

        if (list.isEmpty()) {
            throw new RuntimeException("Payment not found for order: " + orderUuid);
        }

        Payment payment = list.get(0);

        if ("SUCCESS".equals(payment.getStatus())) {
            log.info("Payment already SUCCESS for order {} — idempotent, no-op", orderUuid);
            return payment;
        }

        if ("FAILED".equals(payment.getStatus())) {
            log.warn("Payment already FAILED for order {} — cannot mark SUCCESS; ignoring", orderUuid);
            return payment;
        }

        payment.setStatus("SUCCESS");

        Payment saved = paymentRepository.save(payment);

        PaymentCompletedEvent event = buildCompletedEvent(saved);

        outboxEventService.save(TOPIC_PAYMENT_SUCCESS, saved.getOrderUuid(), event);

        log.info("Payment SUCCESS and outbox event queued for order {}", saved.getOrderUuid());

        return saved;
    }

    @Transactional
    public Payment markFailed(String orderUuid, String reason) {
        List<Payment> list = paymentRepository.findByOrderUuid(orderUuid);

        if (list.isEmpty()) {
            throw new RuntimeException("Payment not found for order: " + orderUuid);
        }

        Payment payment = list.get(0);

        if ("FAILED".equals(payment.getStatus())) {
            log.info("Payment already FAILED for order {} — idempotent, no-op", orderUuid);
            return payment;
        }

        if ("SUCCESS".equals(payment.getStatus())) {
            log.warn("Payment already SUCCESS for order {} — cannot mark FAILED; ignoring", orderUuid);
            return payment;
        }

        payment.setStatus("FAILED");
        payment.setFailureReason(reason);

        Payment saved = paymentRepository.save(payment);

        PaymentFailedEvent failEvent = buildFailedEvent(saved, reason);

        outboxEventService.save(TOPIC_PAYMENT_FAILED, saved.getOrderUuid(), failEvent);

        log.warn("Payment FAILED and outbox event queued for order {} reason={}", orderUuid, reason);

        return saved;
    }

    @Transactional
    public Payment manualPayment(Payment payment) {
        payment.setCreatedAt(Instant.now());
        payment.setPaymentUuid(UUID.randomUUID().toString());

        if (payment.getStatus() == null) {
            payment.setStatus("SUCCESS");
        }

        Payment saved = paymentRepository.save(payment);

        PaymentCompletedEvent event = buildCompletedEvent(saved);

        outboxEventService.save(TOPIC_PAYMENT_SUCCESS, saved.getOrderUuid(), event);

        log.info("Manual payment processed and outbox event queued for order {}", saved.getOrderUuid());

        return saved;
    }

    public Payment getByPaymentUuid(String paymentUuid) {
        return paymentRepository.findByPaymentUuid(paymentUuid).orElse(null);
    }

    public List<Payment> getByOrderUuid(String orderUuid) {
        return paymentRepository.findByOrderUuid(orderUuid);
    }

    public List<Payment> getByCustomerEmail(String customerEmail) {
        return paymentRepository.findByCustomerEmail(customerEmail);
    }

    public Payment updatePayment(String paymentUuid, Payment paymentUpdate) {

        Payment existing = paymentRepository
                .findByPaymentUuid(paymentUuid)
                .orElse(null);

        if (existing == null) {
            return null;
        }

        if (paymentUpdate.getStatus() != null) {
            existing.setStatus(paymentUpdate.getStatus());
        }

        if (paymentUpdate.getMethod() != null) {
            existing.setMethod(paymentUpdate.getMethod());
        }

        return paymentRepository.save(existing);
    }

    private PaymentCompletedEvent buildCompletedEvent(Payment payment) {
        return PaymentCompletedEvent.builder()
                .orderUuid(payment.getOrderUuid())
                .customerEmail(payment.getCustomerEmail())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .build();
    }

    private PaymentFailedEvent buildFailedEvent(Payment payment, String reason) {
        return PaymentFailedEvent.builder()
                .orderUuid(payment.getOrderUuid())
                .customerEmail(payment.getCustomerEmail())
                .amount(payment.getAmount())
                .reason(reason)
                .build();
    }
}
