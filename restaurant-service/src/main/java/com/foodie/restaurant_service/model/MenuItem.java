package com.foodie.restaurant_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {
    @Id
    private String id;               // unique menu item ID
    private String restaurantName;     // link to Restaurant
    private String name;
    private String description;
    private double price;
    private boolean available;

}
