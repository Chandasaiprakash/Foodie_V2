 package com.foodie.restaurant_service.repository;

import com.foodie.restaurant_service.model.Restaurant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;


import java.util.List;

public interface RestaurantRepository extends MongoRepository<Restaurant, String> {

    // 1. Keep the old methods for compatibility
    List<Restaurant> findByCuisineType(String cuisineType);
    Restaurant findByRestaurantId(String restaurantId);
    Restaurant deleteByRestaurantId(String restaurantId);
    List<Restaurant> findByAddress(String address);
    // 2. Remove the basic search method (findByRestaurantNameContainingIgnoreCase)

    // 3. New, advanced full-text search method
    // The $text operator is handled implicitly by the TextCriteria/TextScore
    @Query("{$text: {$search: ?0}}")
    List<Restaurant> searchByText(String text, Sort sort); // Pass the query and a Sort object
}