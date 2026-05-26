package com.foodie.order_service.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private String name;
    private Integer quantity;
    private Double price;
}

