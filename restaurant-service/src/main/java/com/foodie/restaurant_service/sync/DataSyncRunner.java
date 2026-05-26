// in restaurant-service: .../sync/DataSyncRunner.java
package com.foodie.restaurant_service.sync;

import com.foodie.restaurant_service.model.Restaurant;
import com.foodie.restaurant_service.model.RestaurantDocument;
import com.foodie.restaurant_service.model.MenuItemDocument;
import com.foodie.restaurant_service.repository.RestaurantRepository;
import com.foodie.restaurant_service.repository.RestaurantSearchRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataSyncRunner implements CommandLineRunner {

    private final RestaurantRepository restaurantRepository; // Your MongoDB repository
    private final RestaurantSearchRepository searchRepository; // Your Elasticsearch repository
    private static final Logger logger = LoggerFactory.getLogger(DataSyncRunner.class);

    @Override
    public void run(String... args) throws Exception {
        try {
            syncRestaurantsToSearch();
        } catch (Exception ex) {
            logger.warn("Skipping Elasticsearch sync because the search service is unavailable: {}", ex.getMessage());
        }
    }

    private void syncRestaurantsToSearch() {
        if (searchRepository.count() == 0) {
            logger.info("Elasticsearch index is empty. Starting data sync from MongoDB...");

            // Fetch all restaurants from MongoDB
            List<Restaurant> allRestaurants = restaurantRepository.findAll();

            if (allRestaurants.isEmpty()) {
                logger.warn("No restaurants found in MongoDB to sync.");
                return;
            }

            // Convert and save to Elasticsearch
            List<RestaurantDocument> restaurantDocuments = allRestaurants.stream()
                    .map(this::mapToDocument)
                    .collect(Collectors.toList());

            searchRepository.saveAll(restaurantDocuments);

            logger.info("Successfully synced {} restaurants to Elasticsearch.", restaurantDocuments.size());
        } else {
            logger.info("Elasticsearch index already contains data. Skipping sync.");
        }
    }

    // You can reuse this mapping logic from your service
    private RestaurantDocument mapToDocument(Restaurant restaurant) {
        RestaurantDocument doc = new RestaurantDocument();
        doc.setRestaurantId(restaurant.getRestaurantId());
        doc.setRestaurantName(restaurant.getRestaurantName());
        doc.setCuisineType(restaurant.getCuisineType());
        doc.setAddress(restaurant.getAddress());
        doc.setRating(restaurant.getRating());

        if (restaurant.getMenu() != null) {
            doc.setMenu(restaurant.getMenu().stream().map(item -> {
                MenuItemDocument menuItemDoc = new MenuItemDocument();
                menuItemDoc.setName(item.getName());
                menuItemDoc.setDescription(item.getDescription());
                return menuItemDoc;
            }).collect(Collectors.toList()));
        }
        return doc;
    }
}
