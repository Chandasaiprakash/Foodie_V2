package com.foodie.cart_service;

import com.foodie.cart_service.model.Cart;
import com.foodie.cart_service.model.CartItem;
import com.foodie.cart_service.repository.CartRepository;
import com.foodie.cart_service.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private CartService cartService;

    private Cart sampleCart;

    @BeforeEach
    void setUp() {
        CartItem item = new CartItem("Burger", 2, 5.99);
        sampleCart = Cart.builder()
                .id("cart-1")
                .userEmail("user@example.com")
                .items(List.of(item))
                .build();
    }

    @Test
    void getCartByUser_returnsCart_whenFound() {
        when(cartRepository.findByUserEmail("user@example.com")).thenReturn(Optional.of(sampleCart));

        Cart result = cartService.getCartByUser("user@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getUserEmail()).isEqualTo("user@example.com");
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void getCartByUser_returnsNull_whenNotFound() {
        when(cartRepository.findByUserEmail("ghost@example.com")).thenReturn(Optional.empty());

        Cart result = cartService.getCartByUser("ghost@example.com");

        assertThat(result).isNull();
    }

    @Test
    void getAllCarts_returnsAllCarts() {
        Cart second = Cart.builder().id("cart-2").userEmail("other@example.com").items(List.of()).build();
        when(cartRepository.findAll()).thenReturn(List.of(sampleCart, second));

        List<Cart> carts = cartService.getAllCarts();

        assertThat(carts).hasSize(2);
    }

    @Test
    void saveCart_persistsAndReturnsCart() {
        when(cartRepository.save(sampleCart)).thenReturn(sampleCart);

        Cart saved = cartService.saveCart(sampleCart);

        assertThat(saved.getId()).isEqualTo("cart-1");
        verify(cartRepository).save(sampleCart);
    }

    @Test
    void deleteCart_invokesRepositoryDelete() {
        cartService.deleteCart("cart-1");

        verify(cartRepository).deleteById("cart-1");
    }

    @Test
    void clearCart_deletesCart_whenUserHasCart() {
        when(cartRepository.findByUserEmail("user@example.com")).thenReturn(Optional.of(sampleCart));

        cartService.clearCart("user@example.com");

        verify(cartRepository).delete(sampleCart);
    }

    @Test
    void clearCart_doesNothing_whenUserHasNoCart() {
        when(cartRepository.findByUserEmail("ghost@example.com")).thenReturn(Optional.empty());

        cartService.clearCart("ghost@example.com");

        verify(cartRepository, never()).delete(any());
    }

    @Test
    void saveCart_withMultipleItems_persistsAll() {
        Cart multiItemCart = Cart.builder()
                .id("cart-3")
                .userEmail("multi@example.com")
                .items(List.of(
                        new CartItem("Pizza", 1, 12.99),
                        new CartItem("Coke", 2, 1.99)
                ))
                .build();
        when(cartRepository.save(multiItemCart)).thenReturn(multiItemCart);

        Cart saved = cartService.saveCart(multiItemCart);

        assertThat(saved.getItems()).hasSize(2);
    }
}
