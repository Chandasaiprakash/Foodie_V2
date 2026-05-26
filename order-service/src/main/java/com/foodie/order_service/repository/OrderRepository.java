package com.foodie.order_service.repository;


import com.foodie.order_service.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerEmail(String customerEmail);
    Optional<Order> findByOrderUuid(String orderUuid);
    Page<Order> findByCustomerEmail(String email, Pageable pageable);

    Page<Order> findByCustomerEmailAndRestaurantNameContainingIgnoreCase(String email, String restaurantName, Pageable pageable);



}

