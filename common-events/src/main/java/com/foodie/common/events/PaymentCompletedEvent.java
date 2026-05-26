package com.foodie.common.events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCompletedEvent {
    private String orderUuid;
    private String customerEmail;
    private double amount;
    private String status;
    private String customerPhone;

    /** Correlation ID — propagated from the originating HTTP request through Kafka. */
    private String correlationId;
}
