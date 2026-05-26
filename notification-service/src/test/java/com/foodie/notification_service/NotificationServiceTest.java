package com.foodie.notification_service;

import com.foodie.common.events.DeliveryEvent;
import com.foodie.common.events.OrderUpdatedEvent;
import com.foodie.notification_service.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void broadcast_sendsDeliveryEvent_toTopic() {
        DeliveryEvent event = DeliveryEvent.builder()
                .orderUuid("order-uuid-001")
                .status("DELIVERED")
                .customerEmail("customer@example.com")
                .build();

        notificationService.broadcast(event);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/updates"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(event);
    }

    @Test
    void broadcast_sendsOrderUpdatedEvent_toTopic() {
        OrderUpdatedEvent event = OrderUpdatedEvent.builder()
                .orderUuid("order-uuid-002")
                .status("OUT_FOR_DELIVERY")
                .customerEmail("user@example.com")
                .build();

        notificationService.broadcast(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/updates"), eq(event));
    }

    @Test
    void broadcast_handlesArbitraryObject_withoutError() {
        Object genericEvent = new Object();

        notificationService.broadcast(genericEvent);

        verify(messagingTemplate).convertAndSend(eq("/topic/updates"), eq(genericEvent));
    }

    @Test
    void broadcast_sendsDeliveryEvent_withCorrectStatus() {
        DeliveryEvent event = DeliveryEvent.builder()
                .orderUuid("order-uuid-003")
                .status("ASSIGNED")
                .customerEmail("rider@example.com")
                .build();

        notificationService.broadcast(event);

        // verify /topic/updates was called — WebSocket delivery verified via mock
        verify(messagingTemplate).convertAndSend(eq("/topic/updates"), eq(event));
    }
}
