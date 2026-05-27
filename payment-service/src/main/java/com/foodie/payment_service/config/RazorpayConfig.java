package com.foodie.payment_service.config;

import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RazorpayConfig {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    /**
     * Production Razorpay client.
     *
     * Tests provide their own mocked RazorpayClient via TestRazorpayConfig.
     * ConditionalOnMissingBean prevents bean-definition collisions during
     * integration test ApplicationContext bootstrap.
     */
    @Bean
    @ConditionalOnMissingBean(RazorpayClient.class)
    public RazorpayClient razorpayClient() throws Exception {
        return new RazorpayClient(keyId, keySecret);
    }
}
