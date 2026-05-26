package com.foodie.common.events;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderUpdatedEvent {
    private String orderUuid;
    private String status;         // e.g. CREATED, CONFIRMED, DELIVERED
    private String paymentStatus;  // e.g. SUCCESS, FAILED
    private String customerEmail;

    /** Correlation ID — propagated from the originating HTTP request through Kafka. */
    private String correlationId;
}
