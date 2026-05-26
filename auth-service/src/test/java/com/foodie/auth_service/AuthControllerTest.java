package com.foodie.auth_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodie.auth_service.client.UserClient;
import com.foodie.auth_service.controller.AuthController;
import com.foodie.auth_service.dto.*;
import com.foodie.auth_service.security.JwtAuthFilter;
import com.foodie.auth_service.security.JwtService;
import com.foodie.auth_service.security.SecurityConfig;
import feign.FeignException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserClient userClient;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private BCryptPasswordEncoder passwordEncoder;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private UserAuthDetails buildUserDetails() {
        return new UserAuthDetails(1L, "user@example.com", "CUSTOMER", "hashed-pass", "testuser", "9876543210");
    }

    @Test
    void register_returns200WithToken_onSuccess() throws Exception {
        when(userClient.createUser(any())).thenReturn(buildUserDetails());
        when(jwtService.generateToken(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("jwt-token");

        RegisterRequest req = new RegisterRequest();
        req.setEmail("user@example.com");
        req.setPassword("password123");
        req.setUsername("testuser");
        req.setPhoneNumber("9876543210");
        req.setRole("CUSTOMER");

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void register_returns409_whenEmailAlreadyExists() throws Exception {
        when(userClient.createUser(any())).thenThrow(FeignException.Conflict.class);

        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@example.com");
        req.setPassword("password");

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_returns200WithToken_onValidCredentials() throws Exception {
        UserAuthDetails user = buildUserDetails();
        when(userClient.getUserByEmailForAuth(anyString(), anyString())).thenReturn(user);
        when(passwordEncoder.matches("password123", "hashed-pass")).thenReturn(true);
        when(jwtService.generateToken(anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("jwt-token");

        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_returns401_onWrongPassword() throws Exception {
        when(userClient.getUserByEmailForAuth(anyString(), anyString())).thenReturn(buildUserDetails());
        when(passwordEncoder.matches("wrong", "hashed-pass")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("wrong");

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns401_whenUserServiceFails() throws Exception {
        when(userClient.getUserByEmailForAuth(anyString(), anyString())).thenThrow(new RuntimeException("user-service down"));

        LoginRequest req = new LoginRequest();
        req.setEmail("user@example.com");
        req.setPassword("password123");

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns200WithUserDetails_onValidToken() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("email", String.class)).thenReturn("user@example.com");
        when(claims.get("role", String.class)).thenReturn("CUSTOMER");
        when(claims.get("username", String.class)).thenReturn("testuser");
        when(claims.get("phoneNumber", String.class)).thenReturn("9876543210");

        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.getClaims("valid-token")).thenReturn(claims);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void me_returns401_whenNoAuthHeader() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns401_whenTokenInvalid() throws Exception {
        when(jwtService.validateToken("bad-token")).thenReturn(false);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }
}
