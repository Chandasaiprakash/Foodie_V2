package com.foodie.delivery_service.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("delivery_partners")
public class DeliveryPartner {
    @Id
    private String id;
    private String name;
    private String phone;
    private boolean available;
    private double lastLat;
    private double lastLng;
}

