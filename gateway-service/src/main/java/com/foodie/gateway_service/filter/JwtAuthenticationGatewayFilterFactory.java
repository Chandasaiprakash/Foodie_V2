package com.foodie.gateway_service.filter;

import com.foodie.gateway_service.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationGatewayFilterFactory(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or invalid Authorization header");
            }

            String token = authHeader.substring(7);

            if (!jwtUtil.validateToken(token)) {
                return onError(exchange, "Invalid or expired JWT token");
            }

            // ✅ Extract correct values
            Claims claims = jwtUtil.getClaims(token);
            String email = claims.get("email", String.class); // correct field
            String userId = claims.getSubject(); // "sub" = userId

            // ✅ Set headers cleanly (overwrite, don't append)
            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.set("X-User-Email", email);
                        headers.set("X-User-Id", userId);
                    }))
                    .build();

            return chain.filter(modifiedExchange);
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Collections.emptyList();
    }

    public static class Config {}
}
