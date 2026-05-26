package com.foodie.restaurant_service.service;

import com.foodie.restaurant_service.model.MenuItemDocument;
import com.foodie.restaurant_service.model.Restaurant;
import com.foodie.restaurant_service.model.RestaurantDocument;
import com.foodie.restaurant_service.repository.RestaurantRepository;
import com.foodie.restaurant_service.repository.RestaurantSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort; // ⬅️ NEW IMPORT
import org.springframework.data.mongodb.core.query.TextCriteria; // ⬅️ NEW IMPORT

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestaurantService {
    private final RestaurantRepository restaurantRepository;
    private final RestaurantSearchRepository searchRepository;

    // ... (getAll, getByRestaurantId, save, delete, filterByCuisine)
    public List<Restaurant> getAll() {
        return restaurantRepository.findAll();
    }

    public List<Restaurant> getRestaurantByCity(String address) {
        return restaurantRepository.findByAddress(address);
    }


    public Restaurant getByRestaurantId(String restaurantId) {
        return restaurantRepository.findByRestaurantId(restaurantId);
    }

    private RestaurantDocument mapToDocument(Restaurant restaurant) {
        RestaurantDocument doc = new RestaurantDocument();
        doc.setRestaurantId(restaurant.getRestaurantId());
        doc.setRestaurantName(restaurant.getRestaurantName());
        doc.setCuisineType(restaurant.getCuisineType());
        doc.setAddress(restaurant.getAddress());
        doc.setRating(restaurant.getRating());
        // ✨ NEW: Map menu items
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

public Restaurant save(Restaurant restaurant) {
    Restaurant savedRestaurant = restaurantRepository.save(restaurant);
    searchRepository.save(mapToDocument(savedRestaurant));
    return savedRestaurant;
}

public void delete(String restaurantId) {
    restaurantRepository.deleteByRestaurantId(restaurantId);
    searchRepository.deleteById(restaurantId);
}



public List<Restaurant> filterByCuisine(String cuisineType) {
    return restaurantRepository.findByCuisineType(cuisineType);
}

/* // ❌ Change the signature of this method
 public List<Restaurant> searchByText(String text) {
     // Sort by the relevance score ('textScore') computed by MongoDB
     // The descending order ensures the best matches appear first.
     Sort sortByScore = Sort.by(Sort.Direction.DESC, "score");

     return restaurantRepository.searchByText(text, sortByScore);
 }*/
public List<RestaurantDocument> searchRestaurants(String query, String address) {
    if (address == null || address.isBlank()) {
        // Handle global search if needed, or just search with the query
        // For simplicity, we'll call the multi-field search for now.
        return searchRepository.searchFuzzyAcrossAllFields(query);
    }
    // Call the new, location-aware search method
    return searchRepository.searchFuzzyWithCityFilter(query, address);
}


}