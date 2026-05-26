 package com.foodie.restaurant_service.model;

import com.foodie.restaurant_service.model.MenuItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed; // ⬅️ NEW IMPORT
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "restaurants")
public class Restaurant {
    @Id
    private String restaurantId;

    @TextIndexed(weight = 5) // ⬅️ HIGHEST PRIORITY
    private String restaurantName;

    private String address;

    @TextIndexed(weight = 3) // ⬅️ MEDIUM PRIORITY
    private String cuisineType;

    private double rating;

    @TextIndexed(weight = 1) // ⬅️ LOWEST PRIORITY (searching across embedded menu item names)
    private List<MenuItem> menu = new ArrayList<>(); // NOTE: Menu fields must also be indexed or you search on the whole array. A better approach is often to index 'menu.name'.
}