package com.foodie.payment_service.integration;

import com.foodie.payment_service.config.TestRazorpayConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Reusable Testcontainers base for all payment-service integration tests.
 *
 * Provides:
 *   - Real KafkaContainer (confluentinc/cp-kafka:7.6.1)
 *   - Real MySQLContainer (mysql:8.0)
 *   - DynamicPropertySource wiring into Spring context
 *   - Mocked RazorpayClient (no external calls in tests)
 *   - Eureka + tracing disabled
 */
@Testcontainers
@Import(TestRazorpayConfig.class)
public abstract class KafkaIntegrationTestBase {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0")
    )
        .withDatabaseName("foodie_payment_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Real Kafka broker port from container
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Real MySQL JDBC URL from container
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",
                     () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform",
                     () -> "org.hibernate.dialect.MySQLDialect");

        // Disable service discovery
        registry.add("spring.cloud.discovery.enabled", () -> "false");

        // Disable distributed tracing
        registry.add("management.tracing.enabled", () -> "false");

        // Razorpay stub — TestRazorpayConfig mocks the bean,
        // but these prevent binding failures on @Value injection
        registry.add("razorpay.key-id", () -> "rzp_test_stub");
        registry.add("razorpay.key-secret", () -> "stub_secret");
        registry.add("razorpay.webhook-secret", () -> "stub_webhook");
        registry.add("RAZORPAY_KEY_ID", () -> "rzp_test_stub");
        registry.add("RAZORPAY_KEY_SECRET", () -> "stub_secret");
        registry.add("RAZORPAY_WEBHOOK_SECRET", () -> "stub_webhook");
    }
}
