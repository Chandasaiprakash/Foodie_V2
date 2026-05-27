package com.foodie.order_service.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Reusable Testcontainers base for all order-service integration tests.
 *
 * Uses officially supported Confluent Kafka image because KafkaContainer
 * internally depends on Confluent startup scripts and wait strategies.
 */
@Testcontainers
public abstract class KafkaIntegrationTestBase {

    private static final DockerImageName KAFKA_IMAGE =
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(KAFKA_IMAGE)
                    .withReuse(true)
                    .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")
    )
            .withDatabaseName("foodie_order_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform",
                () -> "org.hibernate.dialect.MySQLDialect");

        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("management.tracing.enabled", () -> "false");

        registry.add("RAZORPAY_KEY_ID", () -> "test_key");
        registry.add("RAZORPAY_KEY_SECRET", () -> "test_secret");
        registry.add("RAZORPAY_WEBHOOK_SECRET", () -> "test_webhook");
    }
}
