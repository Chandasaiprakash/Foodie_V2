package com.foodie.delivery_service;

import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.common.events.DeliveryEvent;
import com.foodie.delivery_service.client.OrderServiceClient;
import com.foodie.delivery_service.model.Delivery;
import com.foodie.delivery_service.model.DeliveryPartner;
import com.foodie.delivery_service.repository.DeliveryPartnerRepository;
import com.foodie.delivery_service.repository.DeliveryRepository;
import com.foodie.delivery_service.service.DeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryPartnerRepository partnerRepository;

    @Mock
    private KafkaTemplate<String, DeliveryEvent> kafkaTemplate;

    @Mock
    private OrderServiceClient orderClient;

    @InjectMocks
    private DeliveryService deliveryService;

    private DeliveryPartner availablePartner;

    @BeforeEach
    void setUp() {
        availablePartner = new DeliveryPartner();
        availablePartner.setId("partner-1");
        availablePartner.setPhone("9999999999");
        availablePartner.setAvailable(true);
    }

    @Test
    void assignForOrder_createsDelivery_withAvailablePartner() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .orderUuid("order-uuid-001")
                .customerEmail("customer@example.com")
                .customerPhone("8888888888")
                .build();
        when(partnerRepository.findByAvailableTrue()).thenReturn(List.of(availablePartner));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Delivery result = deliveryService.assignForOrder(event);

        assertThat(result.getOrderUuid()).isEqualTo("order-uuid-001");
        assertThat(result.getStatus()).isEqualTo("ASSIGNED");
        assertThat(result.getPartnerId()).isEqualTo("partner-1");
        verify(partnerRepository).save(argThat(p -> !p.isAvailable())); // partner marked unavailable
        verify(kafkaTemplate).send(eq("delivery-events"), anyString(), any());
    }

    @Test
    void assignForOrder_createsDelivery_withNoPartnerAvailable() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .orderUuid("order-uuid-002")
                .customerEmail("cust@example.com")
                .customerPhone("1234567890")
                .build();
        when(partnerRepository.findByAvailableTrue()).thenReturn(List.of());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Delivery result = deliveryService.assignForOrder(event);

        assertThat(result.getPartnerId()).isNull();
        assertThat(result.getStatus()).isEqualTo("ASSIGNED");
    }

    @Test
    void assignForOrder_fetchesCustomerInfo_fromOrderService_whenMissing() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .orderUuid("order-uuid-003")
                .customerEmail(null)
                .customerPhone(null)
                .build();
        OrderServiceClient.OrderDto dto = mock(OrderServiceClient.OrderDto.class);
        when(dto.customerEmail()).thenReturn("fetched@example.com");
        when(dto.customerPhone()).thenReturn("5555555555");
        when(orderClient.getOrder("order-uuid-003")).thenReturn(dto);
        when(partnerRepository.findByAvailableTrue()).thenReturn(List.of());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Delivery result = deliveryService.assignForOrder(event);

        assertThat(result.getCustomerEmail()).isEqualTo("fetched@example.com");
        assertThat(result.getCustomerPhone()).isEqualTo("5555555555");
    }

    @Test
    void manualAssign_savesAndPublishesEvent() {
        Delivery delivery = Delivery.builder()
                .orderUuid("order-uuid-004")
                .status("ASSIGNED")
                .build();
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Delivery result = deliveryService.manualAssign(delivery);

        assertThat(result.getAssignedAt()).isNotNull();
        verify(kafkaTemplate).send(eq("delivery-events"), anyString(), any());
    }

    @Test
    void manualAssign_throwsException_whenPartnerAlreadyBusy() {
        availablePartner.setAvailable(false);
        Delivery delivery = Delivery.builder()
                .orderUuid("order-uuid-005")
                .partnerId("partner-1")
                .build();
        when(partnerRepository.findById("partner-1")).thenReturn(Optional.of(availablePartner));

        assertThatThrownBy(() -> deliveryService.manualAssign(delivery))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already busy");
    }

    @Test
    void updateStatus_updatesStatusAndPublishesEvent() {
        Delivery delivery = Delivery.builder()
                .id("delivery-1")
                .orderUuid("order-uuid-006")
                .status("ASSIGNED")
                .build();
        when(deliveryRepository.findById("delivery-1")).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Delivery result = deliveryService.updateStatus("delivery-1", "PICKED_UP");

        assertThat(result.getStatus()).isEqualTo("PICKED_UP");
        verify(kafkaTemplate).send(eq("delivery-events"), anyString(), any());
    }

    @Test
    void updateStatus_freesPartner_whenDelivered() {
        Delivery delivery = Delivery.builder()
                .id("delivery-2")
                .orderUuid("order-uuid-007")
                .status("ON_THE_WAY")
                .partnerId("partner-1")
                .build();
        when(deliveryRepository.findById("delivery-2")).thenReturn(Optional.of(delivery));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(partnerRepository.findById("partner-1")).thenReturn(Optional.of(availablePartner));

        deliveryService.updateStatus("delivery-2", "DELIVERED");

        verify(partnerRepository).save(argThat(DeliveryPartner::isAvailable));
    }

    @Test
    void updateStatus_throwsException_whenDeliveryNotFound() {
        when(deliveryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.updateStatus("missing", "DELIVERED"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Delivery not found");
    }
}
