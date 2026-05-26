package com.foodie.auth_service.dto;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long id;
    private String email;
    private String role;
    private String username;
    private String phoneNumber;

}