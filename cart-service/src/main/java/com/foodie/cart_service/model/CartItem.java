package com.foodie.cart_service.model;

import lombok.*;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class CartItem {
        private String name;
        private int quantity;
        private double price;
    }


