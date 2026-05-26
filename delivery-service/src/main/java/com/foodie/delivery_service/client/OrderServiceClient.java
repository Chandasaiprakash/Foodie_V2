package com.foodie.delivery_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for order-service.
 *
 * <p>Routing is via the K8s ClusterIP DNS name supplied by {@code ORDER_SERVICE_URL}
 * (set in the service ConfigMap).  No Eureka lookup occurs.
 */
@FeignClient(name = "order-service", url = "${order.service.url:http://localhost:8082}")
public interface OrderServiceClient {

    @GetMapping("/orders/{orderUuid}")
    OrderDto getOrder(@PathVariable("orderUuid") String orderUuid);

    record OrderDto(String orderUuid, String customerEmail, String customerPhone) {}
}
