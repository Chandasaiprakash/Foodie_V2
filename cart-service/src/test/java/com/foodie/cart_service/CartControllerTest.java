package com.foodie.cart_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.cart_service.controller.CartController;
import com.foodie.cart_service.model.Cart;
import com.foodie.cart_service.model.CartItem;
import com.foodie.cart_service.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    private Cart buildCart() {
        return Cart.builder()
                .id("cart-1")
                .userEmail("user@example.com")
                .items(List.of(new CartItem("Burger", 2, 5.99)))
                .build();
    }

    @Test
    void getAll_returns200WithCartList() throws Exception {
        when(cartService.getAllCarts()).thenReturn(List.of(buildCart()));

        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userEmail").value("user@example.com"))
                .andExpect(jsonPath("$[0].items[0].name").value("Burger"));
    }

    @Test
    void getByUser_returns200WithCart() throws Exception {
        when(cartService.getCartByUser("user@example.com")).thenReturn(buildCart());

        mockMvc.perform(get("/cart/user@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("user@example.com"));
    }

    @Test
    void getByUser_returnsNullBody_whenCartNotFound() throws Exception {
        when(cartService.getCartByUser("ghost@example.com")).thenReturn(null);

        mockMvc.perform(get("/cart/ghost@example.com"))
                .andExpect(status().isOk());
    }

    @Test
    void addOrUpdateCart_returns200WithSavedCart() throws Exception {
        Cart cart = buildCart();
        when(cartService.saveCart(any(Cart.class))).thenReturn(cart);

        mockMvc.perform(post("/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cart-1"));
    }

    @Test
    void deleteCart_returns200() throws Exception {
        doNothing().when(cartService).deleteCart("cart-1");

        mockMvc.perform(delete("/cart/cart-1"))
                .andExpect(status().isOk());

        verify(cartService).deleteCart("cart-1");
    }

    @Test
    void clearCart_returns200() throws Exception {
        doNothing().when(cartService).clearCart("user@example.com");

        mockMvc.perform(delete("/cart/clear/user@example.com"))
                .andExpect(status().isOk());

        verify(cartService).clearCart("user@example.com");
    }
}
