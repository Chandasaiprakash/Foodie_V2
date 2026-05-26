package com.foodie.delivery_service.controller;


import com.foodie.delivery_service.model.Delivery;
import com.foodie.delivery_service.model.DeliveryPartner;
import com.foodie.delivery_service.repository.DeliveryPartnerRepository;
import com.foodie.delivery_service.repository.DeliveryRepository;
import com.foodie.delivery_service.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final DeliveryPartnerRepository partnerRepo;
    private final DeliveryRepository deliveryRepo;

    @GetMapping("/partners")
    public List<DeliveryPartner> partners() {
        return partnerRepo.findAll();
    }

    @PostMapping("/partners")
    public DeliveryPartner registerPartner(@RequestBody DeliveryPartner partner) {
        partner.setAvailable(true);
        return partnerRepo.save(partner);
    }

    @PostMapping
    public Delivery manualAssign(@RequestBody Delivery d) {
        return deliveryService.manualAssign(d);
    }

    @PutMapping("/{id}/status")
    public Delivery updateStatus(@PathVariable String id, @RequestParam String status) {
        return deliveryService.updateStatus(id, status);
    }

    @GetMapping
    public List<Delivery> all() {
        return deliveryRepo.findAll();
    }

    @GetMapping("/{orderUuid}")
    public List<Delivery> byOrder(@PathVariable String orderUuid) {
        return deliveryRepo.findByOrderUuid(orderUuid);
    }
}

