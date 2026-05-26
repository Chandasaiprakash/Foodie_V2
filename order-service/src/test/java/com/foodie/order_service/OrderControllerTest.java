package com.foodie.order_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.order_service.controller.OrderController;
import com.foodie.order_service.model.Order;
import com.foodie.order_service.model.OrderItem;
import com.foodie.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private Order buildOrder() {
        return Order.builder()
                .id(1L)
                .orderUuid("uuid-001")
                .customerEmail("customer@example.com")
                .restaurantName("Burger Palace")
                .items(List.of(new OrderItem("Burger", 1, 8.99)))
                .total(8.99)
                .status("CREATED")
                .paymentStatus("PENDING")
                .build();
    }

    @Test
    void placeOrder_returns200WithOrder() throws Exception {
        Order order = buildOrder();
        when(orderService.createOrder(any(Order.class), eq("customer@example.com"))).thenReturn(order);

        mockMvc.perform(post("/orders")
                        .header("X-User-Email", "customer@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderUuid").value("uuid-001"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getByUuid_returns200_whenOwned() throws Exception {
        when(orderService.getByUuidIfOwned("uuid-001", "customer@example.com"))
                .thenReturn(Optional.of(buildOrder()));

        mockMvc.perform(get("/orders/uuid-001")
                        .header("X-User-Email", "customer@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Burger Palace"));
    }

    @Test
    void getByUuid_returns403_whenNotOwned() throws Exception {
        when(orderService.getByUuidIfOwned("uuid-001", "attacker@example.com"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/uuid-001")
                        .header("X-User-Email", "attacker@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getByCustomer_returns200_whenEmailMatches() throws Exception {
        when(orderService.getByCustomerEmail("customer@example.com"))
                .thenReturn(List.of(buildOrder()));

        mockMvc.perform(get("/orders/customer/customer@example.com")
                        .header("X-User-Email", "customer@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderUuid").value("uuid-001"));
    }

    @Test
    void getByCustomer_returns403_whenEmailMismatch() throws Exception {
        mockMvc.perform(get("/orders/customer/other@example.com")
                        .header("X-User-Email", "customer@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePaymentStatus_returns200WithMessage() throws Exception {
        doNothing().when(orderService).updatePaymentStatus("uuid-001", "SUCCESS");

        mockMvc.perform(put("/orders/uuid-001/payment-status")
                        .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(content().string("Payment status updated to SUCCESS"));
    }

    @Test
    void deleteOrder_returns200_whenOrderExists() throws Exception {
        when(orderService.deleteOrder("uuid-001")).thenReturn(true);

        mockMvc.perform(delete("/orders/uuid-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Order deleted successfully"));
    }

    @Test
    void deleteOrder_returns404_whenOrderNotFound() throws Exception {
        when(orderService.deleteOrder("missing")).thenReturn(false);

        mockMvc.perform(delete("/orders/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found"));
    }

    @Test
    void reorder_returns200WithNewOrder() throws Exception {
        Order order = buildOrder();
        when(orderService.createOrder(any(Order.class), eq("customer@example.com"))).thenReturn(order);

        mockMvc.perform(post("/orders/reorder")
                        .header("X-User-Email", "customer@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Burger Palace"));
    }

    @Test
    void getPagedOrders_returns200() throws Exception {
        Map<String, Object> pagedResponse = Map.of(
                "orders", List.of(buildOrder()),
                "currentPage", 0,
                "totalItems", 1L,
                "totalPages", 1
        );
        when(orderService.getPagedOrders(eq("customer@example.com"), eq(0), eq(10), anyString(), isNull()))
                .thenReturn(pagedResponse);

        mockMvc.perform(get("/orders/customer/customer@example.com/paged")
                        .header("X-User-Email", "customer@example.com")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
    }
}
