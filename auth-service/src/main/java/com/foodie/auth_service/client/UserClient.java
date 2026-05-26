package com.foodie.auth_service.client;

import com.foodie.auth_service.dto.RegisterRequest;
import com.foodie.auth_service.dto.UserAuthDetails;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for user-service.
 *
 * <p>The {@code url} attribute resolves to the K8s ClusterIP DNS name injected
 * via the {@code USER_SERVICE_URL} environment variable.  In-cluster this is
 * set to {@code http://user-service.foodie.svc.cluster.local} by the ConfigMap.
 * The {@code name} attribute is still required by Feign for bean naming; it has
 * no effect on routing now that Eureka is removed.
 */
@FeignClient(name = "user-service", url = "${user.service.url:http://localhost:8085}")
public interface UserClient {

    @PostMapping("/users")
    UserAuthDetails createUser(@RequestBody RegisterRequest request);

    @GetMapping("/internal/users/by-email/{email}")
    UserAuthDetails getUserByEmailForAuth(
            @PathVariable("email") String email,
            @RequestHeader("X-Internal-Secret") String internalSecret);
}
