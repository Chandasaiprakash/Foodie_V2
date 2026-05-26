package com.foodie.payment_service.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
public class RazorpayService {

    private final RazorpayClient client;

    @Value("${razorpay.key-secret}")
    private String secret;

    public RazorpayService(RazorpayClient client) {
        this.client = client;
    }

    public JSONObject createOrder(String orderUuid, double amount) throws Exception {
        JSONObject options = new JSONObject();

        // Explicitly cast to Object (or use put(String, Object) overload)
        options.put("amount", (Object)(int) (amount * 100)); // in paise
        options.put("currency", (Object)"INR");
        options.put("receipt", (Object)orderUuid);
        options.put("payment_capture", (Object)1); // Also good practice for this int

        Order order = client.orders.create(options); // Also remember to fix 'orders' to 'Orders'

        JSONObject resp = new JSONObject();
        resp.put("id", (Object)order.get("id"));
        resp.put("amount", (Object)order.get("amount"));
        resp.put("currency", (Object)order.get("currency"));
        return resp;
    }
    public boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());
            String expected = Base64.getEncoder().encodeToString(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
