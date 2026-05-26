package com.foodie.payment_service.model;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String paymentUuid;

    private String orderUuid;
    private String customerEmail;
    private Double amount;

    private String method;  // UPI, CARD, WALLET, COD
    private String transactionId;
    private String status;  // SUCCESS, FAILED, PENDING

    private Instant createdAt;
    private String failureReason;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (paymentUuid == null) paymentUuid = UUID.randomUUID().toString(); // auto-generate
    }
}

