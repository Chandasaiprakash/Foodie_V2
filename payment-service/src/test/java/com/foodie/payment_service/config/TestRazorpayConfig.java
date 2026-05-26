package com.foodie.payment_service.config;

import com.razorpay.RazorpayClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Replaces the real RazorpayClient bean in integration tests.
 * The real RazorpayConfig.razorpayClient() tries to connect to Razorpay on
 * construction — this mock prevents that.
 */
@TestConfiguration
public class TestRazorpayConfig {

    @Bean
    @Primary
    public RazorpayClient razorpayClient() {
        return mock(RazorpayClient.class);
    }
}
