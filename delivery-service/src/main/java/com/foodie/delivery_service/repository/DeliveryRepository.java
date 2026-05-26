package com.foodie.delivery_service.repository;


import com.foodie.delivery_service.model.Delivery;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface DeliveryRepository extends MongoRepository<Delivery, String> {
    List<Delivery> findByOrderUuid(String orderUuid);
}

