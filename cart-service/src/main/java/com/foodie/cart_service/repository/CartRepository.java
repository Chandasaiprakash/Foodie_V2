package com.foodie.cart_service.repository;

import com.foodie.cart_service.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CartRepository extends MongoRepository<Cart, String> {
    Optional<Cart> findByUserEmail(String email);
}
