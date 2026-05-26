package com.foodie.payment_service.model;

public class PaymentRequest {
    private String orderUuid;
    private String customerEmail;
    private Double amount;
    private String method; // upi, card, netbanking, cod
    // + getters/setters
}

