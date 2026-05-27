package com.foodie.payment_service;

import com.foodie.common.events.OrderCreatedEvent;
import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.common.events.PaymentFailedEvent;
import com.foodie.payment_service.model.Payment;
import com.foodie.payment_service.outbox.OutboxEventService;
import com.foodie.payment_service.repository.PaymentRepository;
import com.foodie.payment_service.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        pendingPayment = Payment.builder()
                .id(1L)
                .paymentUuid("pay-uuid-001")
                .orderUuid("order-uuid-001")
                .customerEmail("customer@example.com")
                .amount(99.99)
                .method("ONLINE")
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void processPayment_createsPendingPaymentRecord() {

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderUuid("order-uuid-001")
                .customerEmail("customer@example.com")
                .total(99.99)
                .build();

        when(paymentRepository.findByOrderUuid("order-uuid-001"))
                .thenReturn(List.of());

        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.processPayment(event);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getOrderUuid()).isEqualTo("order-uuid-001");
        assertThat(result.getCustomerEmail()).isEqualTo("customer@example.com");
        assertThat(result.getAmount()).isEqualTo(99.99);
        assertThat(result.getPaymentUuid()).isNotBlank();
    }

    @Test
    void markSuccess_updatesStatusAndPublishesEvent() {
        when(paymentRepository.findByOrderUuid("order-uuid-001")).thenReturn(List.of(pendingPayment));
        when(paymentRepository.save(any())).thenReturn(pendingPayment);

        Payment result = paymentService.markSuccess("order-uuid-001");

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(outboxEventService).save(eq("payment-completed"), eq("order-uuid-001"), isA(PaymentCompletedEvent.class));
    }

    @Test
    void markSuccess_throwsException_whenOrderNotFound() {
        when(paymentRepository.findByOrderUuid("missing")).thenReturn(List.of());

        assertThatThrownBy(() -> paymentService.markSuccess("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    void markFailed_updatesStatusAndPublishesFailedEvent() {
        when(paymentRepository.findByOrderUuid("order-uuid-001")).thenReturn(List.of(pendingPayment));
        when(paymentRepository.save(any())).thenReturn(pendingPayment);

        Payment result = paymentService.markFailed("order-uuid-001", "Insufficient funds");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(outboxEventService).save(eq("payment-failed"), eq("order-uuid-001"), isA(PaymentFailedEvent.class));
    }

    @Test
    void markFailed_throwsException_whenOrderNotFound() {
        when(paymentRepository.findByOrderUuid("missing")).thenReturn(List.of());

        assertThatThrownBy(() -> paymentService.markFailed("missing", "no reason"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void manualPayment_setsCreatedAtAndPublishesEvent() {
        Payment request = Payment.builder()
                .orderUuid("order-uuid-002")
                .customerEmail("test@example.com")
                .amount(50.00)
                .build();

        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.manualPayment(request);

        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getPaymentUuid()).isNotBlank();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");

        verify(outboxEventService).save(eq("payment-completed"), eq("order-uuid-002"), isA(PaymentCompletedEvent.class));
    }

    @Test
    void createPendingForOrder_savesWithPendingStatus() {

        when(paymentRepository.findByOrderUuid("order-uuid-003"))
                .thenReturn(List.of());

        when(paymentRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPendingForOrder("order-uuid-003", "user@x.com", 25.00);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getOrderUuid()).isEqualTo("order-uuid-003");
    }

    @Test
    void getByPaymentUuid_returnsPayment_whenFound() {
        when(paymentRepository.findByPaymentUuid("pay-uuid-001")).thenReturn(Optional.of(pendingPayment));

        Payment result = paymentService.getByPaymentUuid("pay-uuid-001");

        assertThat(result).isNotNull();
        assertThat(result.getOrderUuid()).isEqualTo("order-uuid-001");
    }

    @Test
    void getByPaymentUuid_returnsNull_whenNotFound() {
        when(paymentRepository.findByPaymentUuid("missing")).thenReturn(Optional.empty());

        Payment result = paymentService.getByPaymentUuid("missing");

        assertThat(result).isNull();
    }

    @Test
    void updatePayment_updatesFields_andSaves() {
        when(paymentRepository.findByPaymentUuid("pay-uuid-001")).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment update = Payment.builder().status("SUCCESS").method("CARD").build();
        Payment result = paymentService.updatePayment("pay-uuid-001", update);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);

        verify(paymentRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getMethod()).isEqualTo("CARD");
    }

    @Test
    void updatePayment_returnsNull_whenPaymentNotFound() {
        when(paymentRepository.findByPaymentUuid("missing")).thenReturn(Optional.empty());

        Payment result = paymentService.updatePayment("missing", new Payment());

        assertThat(result).isNull();
    }
}
