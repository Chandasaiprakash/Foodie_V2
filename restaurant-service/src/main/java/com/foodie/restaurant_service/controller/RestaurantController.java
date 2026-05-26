package com.foodie.restaurant_service.controller;


import com.foodie.restaurant_service.model.Restaurant;
import com.foodie.restaurant_service.model.RestaurantDocument;
import com.foodie.restaurant_service.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

   /* @GetMapping
    public List<Restaurant> getAll() {
        return restaurantService.getAll();
    }*/

    // ✨ UPDATED: Endpoint to get all restaurants now accepts location params
    @GetMapping
    public ResponseEntity<List<Restaurant>> getRestaurantsByLocation(
            @RequestParam String location) {

        return ResponseEntity.ok(restaurantService.getRestaurantByCity(location));
    }

    /*@GetMapping("/search")
    public List<Restaurant> search(@RequestParam String query) { // ⬅️ Renamed for clarity
        return restaurantService.searchByText(query); // ⬅️ Changed method call
    }*/

    @GetMapping("/search")
    public ResponseEntity<List<RestaurantDocument>> searchRestaurants(@RequestParam String query,@RequestParam(required = false) String address) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(query,address));
    }

    @GetMapping("/{restaurantId}")
    public Restaurant getByRestaurantId(@PathVariable String restaurantId) {
        return restaurantService.getByRestaurantId(restaurantId);
    }

    @PostMapping
    public Restaurant add(@RequestBody Restaurant restaurant) {
        return restaurantService.save(restaurant);
    }

    @DeleteMapping("/{restaurantId}")
    public void delete(@PathVariable String restaurantId) {
        restaurantService.delete(restaurantId);
    }



    @GetMapping("/filter")
    public List<Restaurant> filter(@RequestParam String cuisine) {
        return restaurantService.filterByCuisine(cuisine);
    }
}

