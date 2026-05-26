package com.foodie.delivery_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * IDEMPOTENCY FIX — unique index on orderUuid:
 *
 *   @Indexed(unique = true) on orderUuid is a second layer of protection.
 *   Even if the idempotency guard in PaymentEventListener somehow lets a
 *   concurrent duplicate slip through (e.g. two pods starting up simultaneously
 *   before the MongoDB index is fully warmed), the DB-level unique constraint
 *   prevents a second Delivery document from being inserted for the same order.
 *
 *   The application catches DuplicateKeyException in DeliveryService.assignForOrder()
 *   and returns the existing delivery record instead of throwing.
 *
 *   This gives us defense-in-depth:
 *     Layer 1: IdempotencyService.claim() — prevents processing in most cases
 *     Layer 2: Delivery.orderUuid unique index — prevents data corruption if Layer 1 races
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("deliveries")
public class Delivery {

    @Id
    private String id;

    /** Unique per order — no two delivery documents for the same orderUuid. */
    @Indexed(unique = true)
    private String orderUuid;

    private String partnerId;
    private String deliveryPersonEmail;
    private String status; // ASSIGNED, PICKED_UP, ON_THE_WAY, DELIVERED
    private Instant assignedAt;
    private Instant updatedAt;
    private String customerEmail;
    private String customerPhone;
}
