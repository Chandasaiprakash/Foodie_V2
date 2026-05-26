package com.foodie.cart_service.service;

import com.foodie.cart_service.model.Cart;
import com.foodie.cart_service.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;

    public Cart getCartByUser(String email) {
        return cartRepository.findByUserEmail(email).orElse(null);
    }

    public List<Cart> getAllCarts() {
        return cartRepository.findAll();
    }

    public Cart saveCart(Cart cart) {
        return cartRepository.save(cart);
    }

    public void deleteCart(String id) {
        cartRepository.deleteById(id);
    }

    public void clearCart(String email) {
        cartRepository.findByUserEmail(email).ifPresent(cartRepository::delete);
    }
}
