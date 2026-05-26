package com.foodie.notification_service.service;

import com.foodie.common.events.DeliveryEvent;
import com.foodie.common.events.OrderUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    // This will broadcast any event object to the public topic
    public void broadcast(Object event) {
        messagingTemplate.convertAndSend("/topic/updates", event);
        log.info("📡 Broadcasted event to /topic/updates: {}", event);

        // Simulate email/sms by logging
        String orderUuid = "N/A";
        String status = "N/A";
        String customerEmail = "N/A";

        if (event instanceof DeliveryEvent) {
            orderUuid = ((DeliveryEvent) event).getOrderUuid();
            status = ((DeliveryEvent) event).getStatus();
            customerEmail = ((DeliveryEvent) event).getCustomerEmail();
        } else if (event instanceof OrderUpdatedEvent) {
            orderUuid = ((OrderUpdatedEvent) event).getOrderUuid();
            status = ((OrderUpdatedEvent) event).getStatus();
            customerEmail = ((OrderUpdatedEvent) event).getCustomerEmail();
        }

        System.out.printf("[NOTIFICATION] Order %s status %s (customer %s)\n",
                orderUuid, status, customerEmail);
    }
}