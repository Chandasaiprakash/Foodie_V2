package com.foodie.user_service.dto;

// User Service: com.foodie.user_service.dto.UserAuthDetails
// This DTO will be shared/duplicated in the Auth Service for the Feign client.
public record UserAuthDetails(
        Long id,
        String email,
        String password,
        String role,
        String username,
        String phoneNumber
) {}
