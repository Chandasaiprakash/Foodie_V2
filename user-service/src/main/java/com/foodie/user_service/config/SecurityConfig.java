// In user-service: com.foodie.user_service.config.SecurityConfig
package com.foodie.user_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // You should already have this bean from the previous step
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF, as we are using a stateless REST API
                .csrf(csrf -> csrf.disable())

                // 2. Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Allow POST requests to /users for registration without authentication
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()
                        .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        // Secure all other requests
                        .anyRequest().authenticated()
                )

                // 3. Set session management to stateless
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}