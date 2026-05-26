package com.foodie.payment_service.model;

import java.util.UUID;

public class PaymentResponse {
    private UUID orderUuid;
    private String paymentStatus;

    public PaymentResponse(UUID orderUuid, String paymentStatus) {
        this.orderUuid = orderUuid;
        this.paymentStatus = paymentStatus;
    }
}

