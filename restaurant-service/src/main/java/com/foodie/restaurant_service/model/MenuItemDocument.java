package com.foodie.restaurant_service.model;

// in restaurant-service: .../model/MenuItemDocument.java


import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
public class MenuItemDocument {
    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;
}
