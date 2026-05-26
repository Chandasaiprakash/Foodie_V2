package com.foodie.payment_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@RestController
@RequestMapping("/webhooks/razorpay")
public class RazorpayWebhookController {

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, @RequestHeader("X-Razorpay-Signature") String signature) {
        if (!verifySignature(payload, signature)) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }
        // Parse event type (payment.captured / failed etc.)
        // Update Payment & Order entities accordingly
        return ResponseEntity.ok("Received");
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());
            String expected = Base64.getEncoder().encodeToString(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
