package com.foodie.delivery_service.repository;


import com.foodie.delivery_service.model.DeliveryPartner;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeliveryPartnerRepository extends MongoRepository<DeliveryPartner, String> {
    List<DeliveryPartner> findByAvailableTrue();
}

