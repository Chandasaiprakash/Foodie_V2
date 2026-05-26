package com.foodie.payment_service.repository;


import com.foodie.payment_service.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderUuid(String orderUuid);
    List<Payment> findByCustomerEmail(String customerEmail);
    Optional<Payment> findFirstByOrderUuidOrderByCreatedAtDesc(String orderUuid);
    Optional<Payment> findByPaymentUuid(String paymentUuid);
}

