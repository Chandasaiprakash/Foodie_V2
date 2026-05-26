package com.foodie.auth_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    // BCrypt encoder for password hashing
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // Disable CSRF because we’re stateless
                .csrf(csrf -> csrf.disable())

                // Stateless session — required for JWT
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CORS config (if frontend on another domain)
                .cors(cors -> {}) // use default Spring Boot CORS config or customize separately

                // 🔒 Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",       // login/register/me
                                "/ws/**",         // WebSocket endpoints if used
                                "/error",         // Spring’s internal error endpoint
                                "/actuator/**",   // health, metrics, etc.
                                "/swagger-ui/**", // Swagger UI
                                "/v3/api-docs/**" // OpenAPI docs
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // 🧱 Exception handling (no forwarding to /error)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, ex2) -> {
                            res.setStatus(HttpStatus.UNAUTHORIZED.value());
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Unauthorized or invalid token\"}");
                        })
                        .accessDeniedHandler((req, res, ex1) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"Access denied\"}");
                        })
                )

                // 🔐 Add JWT filter before username-password filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
