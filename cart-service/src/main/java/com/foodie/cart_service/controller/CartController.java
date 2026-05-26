package com.foodie.cart_service.controller;

import com.foodie.cart_service.model.Cart;
import com.foodie.cart_service.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public List<Cart> getAll() {
        return cartService.getAllCarts();
    }

    @GetMapping("/{email}")
    public Cart getByUser(@PathVariable String email) {
        return cartService.getCartByUser(email);
    }

    @PostMapping
    public Cart addOrUpdateCart(@RequestBody Cart cart) {
        return cartService.saveCart(cart);
    }

    @DeleteMapping("/{id}")
    public void deleteCart(@PathVariable String id) {
        cartService.deleteCart(id);
    }

    @DeleteMapping("/clear/{email}")
    public void clearCart(@PathVariable String email) {
        cartService.clearCart(email);
    }
}
