package com.foodie.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFailedEvent {
    private String orderUuid;
    private String customerEmail;
    private Double amount;
    private String reason;

    @Default
    private String status = "FAILED";

    /** Correlation ID — propagated from the originating HTTP request through Kafka. */
    private String correlationId;
}
