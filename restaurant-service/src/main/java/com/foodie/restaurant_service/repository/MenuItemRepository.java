package com.foodie.restaurant_service.repository;

import com.foodie.restaurant_service.model.MenuItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MenuItemRepository extends MongoRepository<MenuItem, String> {
    List<MenuItem> findByRestaurantName(String restaurantName);
}
