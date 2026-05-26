// in restaurant-service: .../model/RestaurantDocument.java
package com.foodie.restaurant_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import lombok.Data;

import java.util.List;

@Data
@Document(indexName = "restaurants") // This is the name of the search index
public class RestaurantDocument {

    @Id
    private String restaurantId;

    @Field(type = FieldType.Text)
    private String restaurantName;

    @Field(type = FieldType.Text)
    private String cuisineType;

    // You can add other fields you want to display in search results
    private String address;
    private double rating;
    @Field(type = FieldType.Nested) // ✨ ADD THIS ANNOTATION
    private List<MenuItemDocument> menu;
}