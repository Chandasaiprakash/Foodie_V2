package com.foodie.order_service.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // readable external id
    @Column(unique = true, nullable = false)
    private String orderUuid;

    @Column(nullable = false)
    private String customerEmail;
    private String restaurantName;
    private String customerPhone;
    private String restaurantId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items;

    private Double total;

    private String status; // CREATED, CONFIRMED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, CANCELLED
    private String paymentStatus; // PENDING, SUCCESS, FAILED
    private Instant createdAt;



}

