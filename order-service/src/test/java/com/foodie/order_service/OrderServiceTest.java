package com.foodie.order_service;

import com.foodie.order_service.model.Order;
import com.foodie.order_service.model.OrderItem;
import com.foodie.order_service.outbox.OutboxEventService;
import com.foodie.order_service.repository.OrderRepository;
import com.foodie.order_service.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private OrderService orderService;

    private Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = Order.builder()
                .id(1L)
                .orderUuid("order-uuid-123")
                .customerEmail("customer@example.com")
                .customerPhone("9876543210")
                .restaurantId("rest-001")
                .restaurantName("Burger Palace")
                .items(List.of(new OrderItem("Burger", 2, 8.99)))
                .total(17.98)
                .status("CREATED")
                .paymentStatus("PENDING")
                .build();
    }

    @Test
    void createOrder_setsFieldsCorrectlyAndPublishesKafkaEvent() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order request = Order.builder()
                .restaurantId("rest-001")
                .restaurantName("Burger Palace")
                .customerPhone("9876543210")
                .items(List.of(new OrderItem("Pizza", 1, 12.00)))
                .build();

        Order result = orderService.createOrder(request, "customer@example.com");

        assertThat(result.getCustomerEmail()).isEqualTo("customer@example.com");
        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getPaymentStatus()).isEqualTo("PENDING");
        assertThat(result.getOrderUuid()).isNotBlank();
        assertThat(result.getTotal()).isEqualTo(12.00);
        verify(outboxEventService).save(eq("order-created"), anyString(), any());
    }

    @Test
    void createOrder_calculatesTotal_fromItems() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order request = Order.builder()
                .restaurantId("r1")
                .restaurantName("Pizza Hut")
                .customerPhone("1234567890")
                .items(List.of(
                        new OrderItem("Pasta", 2, 5.00),
                        new OrderItem("Salad", 3, 3.00)
                ))
                .build();

        Order result = orderService.createOrder(request, "test@example.com");

        // 2*5.00 + 3*3.00 = 19.00
        assertThat(result.getTotal()).isEqualTo(19.00);
    }

    @Test
    void getById_returnsOrder_whenFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(sampleOrder));

        Optional<Order> result = orderService.getById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getOrderUuid()).isEqualTo("order-uuid-123");
    }

    @Test
    void getByUuid_returnsOrder_whenFound() {
        when(orderRepository.findByOrderUuid("order-uuid-123")).thenReturn(Optional.of(sampleOrder));

        Optional<Order> result = orderService.getByUuid("order-uuid-123");

        assertThat(result).isPresent();
    }

    @Test
    void updatePaymentStatus_updatesStatus_toFailed() {
        when(orderRepository.findByOrderUuid("order-uuid-123")).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any())).thenReturn(sampleOrder);

        orderService.updatePaymentStatus("order-uuid-123", "FAILED");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getStatus()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void updatePaymentStatus_doesNothing_whenOrderNotFound() {
        when(orderRepository.findByOrderUuid("missing")).thenReturn(Optional.empty());

        orderService.updatePaymentStatus("missing", "SUCCESS");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deleteOrder_returnsTrue_andDeletesOrder() {
        when(orderRepository.findByOrderUuid("order-uuid-123")).thenReturn(Optional.of(sampleOrder));

        boolean deleted = orderService.deleteOrder("order-uuid-123");

        assertThat(deleted).isTrue();
        verify(orderRepository).delete(sampleOrder);
    }

    @Test
    void deleteOrder_returnsFalse_whenOrderNotFound() {
        when(orderRepository.findByOrderUuid("missing")).thenReturn(Optional.empty());

        boolean deleted = orderService.deleteOrder("missing");

        assertThat(deleted).isFalse();
        verify(orderRepository, never()).delete(any());
    }

    @Test
    void getByUuidIfOwned_returnsOrder_whenEmailMatches() {
        when(orderRepository.findByOrderUuid("order-uuid-123")).thenReturn(Optional.of(sampleOrder));

        Optional<Order> result = orderService.getByUuidIfOwned("order-uuid-123", "customer@example.com");

        assertThat(result).isPresent();
    }

    @Test
    void getByUuidIfOwned_returnsEmpty_whenEmailDoesNotMatch() {
        when(orderRepository.findByOrderUuid("order-uuid-123")).thenReturn(Optional.of(sampleOrder));

        Optional<Order> result = orderService.getByUuidIfOwned("order-uuid-123", "attacker@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void getByCustomerEmail_returnsAllOrders() {
        when(orderRepository.findByCustomerEmail("customer@example.com")).thenReturn(List.of(sampleOrder));

        List<Order> orders = orderService.getByCustomerEmail("customer@example.com");

        assertThat(orders).hasSize(1);
    }

    @Test
    void getPagedOrders_returnsPagedResponse() {
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderRepository.findByCustomerEmail(eq("customer@example.com"), any(Pageable.class))).thenReturn(page);

        Map<String, Object> result = orderService.getPagedOrders("customer@example.com", 0, 10, "createdAt,desc", null);

        assertThat(result).containsKey("orders");
        assertThat(result).containsKey("totalItems");
        assertThat(result.get("totalItems")).isEqualTo(1L);
    }

    @Test
    void getPagedOrders_withSearch_usesSearchQuery() {
        Page<Order> page = new PageImpl<>(List.of(sampleOrder));
        when(orderRepository.findByCustomerEmailAndRestaurantNameContainingIgnoreCase(
                eq("customer@example.com"), eq("Burger"), any(Pageable.class))).thenReturn(page);

        Map<String, Object> result = orderService.getPagedOrders("customer@example.com", 0, 10, "createdAt,desc", "Burger");

        assertThat(result.get("totalItems")).isEqualTo(1L);
    }
}
