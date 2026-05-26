package com.foodie.delivery_service.service;

import com.foodie.delivery_service.client.OrderServiceClient;
import com.foodie.delivery_service.resilience.ResilientOrderServiceClient;
import com.foodie.common.events.DeliveryEvent;
import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.delivery_service.model.Delivery;
import com.foodie.delivery_service.model.DeliveryPartner;
import com.foodie.delivery_service.outbox.OutboxEventService;
import com.foodie.delivery_service.repository.DeliveryPartnerRepository;
import com.foodie.delivery_service.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * DeliveryService — Outbox Pattern integration.
 *
 * <p>Previously this service called {@code kafkaTemplate.send()} directly after
 * saving the delivery document. That was a dual-write: a crash or Kafka outage
 * between the two steps left the delivery in a state where the order-service and
 * notification-service never heard about it.
 *
 * <p>Now the flow:
 * <ol>
 *   <li>Insert the {@code Delivery} MongoDB document.</li>
 *   <li>Write an {@code OutboxEvent} document immediately after (same logical unit).</li>
 *   <li>{@link com.foodie.delivery_service.outbox.OutboxPoller} publishes to Kafka async.</li>
 * </ol>
 *
 * <p>Idempotency (dual-layer defence) is preserved:
 * <ul>
 *   <li>Layer 1: {@code IdempotencyService.claim()} in {@code PaymentEventListener}.</li>
 *   <li>Layer 2: MongoDB unique index on {@code Delivery.orderUuid} (DuplicateKeyException guard).</li>
 *   <li>Outbox: no duplicate event written on the idempotent (DuplicateKeyException) path.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final OutboxEventService outboxEventService;
    private final ResilientOrderServiceClient orderClient;

    private static final String TOPIC = "delivery-events";

    public Delivery assignForOrder(PaymentCompletedEvent event) {
        String customerEmail = resolveEmail(event);
        String customerPhone = resolvePhone(event);

        Optional<DeliveryPartner> partnerOpt = partnerRepository.findByAvailableTrue().stream().findFirst();

        Delivery d = Delivery.builder()
                .orderUuid(event.getOrderUuid())
                .status("ASSIGNED")
                .assignedAt(Instant.now())
                .updatedAt(Instant.now())
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .build();

        if (partnerOpt.isPresent()) {
            DeliveryPartner p = partnerOpt.get();
            p.setAvailable(false);
            partnerRepository.save(p);
            d.setPartnerId(p.getId());
            d.setDeliveryPersonEmail(p.getPhone());
        }

        try {
            Delivery saved = deliveryRepository.insert(d); // atomic insert, no upsert
            // Queue outbox event immediately after save — near-atomic in single-node,
            // fully atomic in a replica-set environment with @Transactional.
            outboxEventService.save(TOPIC, saved.getOrderUuid(), buildDeliveryEvent(saved));
            log.info("Delivery assigned and outbox event queued for orderUuid={}", saved.getOrderUuid());
            return saved;

        } catch (DuplicateKeyException e) {
            // Idempotent path: delivery already assigned — do NOT queue another outbox event.
            log.warn("Duplicate delivery assignment suppressed for orderUuid={} (unique index hit)",
                     event.getOrderUuid());
            partnerOpt.ifPresent(p -> {
                p.setAvailable(true);
                partnerRepository.save(p);
            });
            List<Delivery> existing = deliveryRepository.findByOrderUuid(event.getOrderUuid());
            if (!existing.isEmpty()) return existing.get(0);
            throw new IllegalStateException("Delivery not found after DuplicateKeyException for order: "
                    + event.getOrderUuid(), e);
        }
    }

    public Delivery manualAssign(Delivery delivery) {
        delivery.setAssignedAt(Instant.now());
        delivery.setUpdatedAt(Instant.now());
        if (delivery.getPartnerId() != null) {
            partnerRepository.findById(delivery.getPartnerId()).ifPresent(p -> {
                if (!p.isAvailable()) throw new RuntimeException("Partner already busy!");
                p.setAvailable(false);
                partnerRepository.save(p);
                delivery.setDeliveryPersonEmail(p.getPhone());
            });
        }
        Delivery saved = deliveryRepository.save(delivery);
        outboxEventService.save(TOPIC, saved.getOrderUuid(), buildDeliveryEvent(saved));
        return saved;
    }

    public Delivery updateStatus(String deliveryId, String status) {
        Delivery d = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new RuntimeException("Delivery not found"));
        d.setStatus(status);
        d.setUpdatedAt(Instant.now());
        Delivery updated = deliveryRepository.save(d);

        if ("DELIVERED".equalsIgnoreCase(status) && d.getPartnerId() != null) {
            partnerRepository.findById(d.getPartnerId()).ifPresent(p -> {
                p.setAvailable(true);
                partnerRepository.save(p);
            });
        }

        outboxEventService.save(TOPIC, updated.getOrderUuid(), buildDeliveryEvent(updated));
        return updated;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DeliveryEvent buildDeliveryEvent(Delivery d) {
        return DeliveryEvent.builder()
                .orderUuid(d.getOrderUuid())
                .status(d.getStatus())
                .deliveryPersonEmail(d.getDeliveryPersonEmail())
                .partnerId(d.getPartnerId())
                .customerEmail(d.getCustomerEmail())
                .customerPhone(d.getCustomerPhone())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private String resolveEmail(PaymentCompletedEvent event) {
        if (event.getCustomerEmail() != null && !event.getCustomerEmail().isBlank()) {
            return event.getCustomerEmail();
        }
        return fetchFromOrderService(event.getOrderUuid()).map(OrderServiceClient.OrderDto::customerEmail).orElse(null);
    }

    private String resolvePhone(PaymentCompletedEvent event) {
        if (event.getCustomerPhone() != null && !event.getCustomerPhone().isBlank()) {
            return event.getCustomerPhone();
        }
        return fetchFromOrderService(event.getOrderUuid()).map(OrderServiceClient.OrderDto::customerPhone).orElse(null);
    }

    private Optional<OrderServiceClient.OrderDto> fetchFromOrderService(String orderUuid) {
        return orderClient.fetchOrder(orderUuid);
    }
}
