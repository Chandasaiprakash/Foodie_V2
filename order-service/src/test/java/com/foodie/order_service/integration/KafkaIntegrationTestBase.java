package com.foodie.order_service.integration;

import org.springframework.boot.test.context.SpringBootTest;
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
 * Containers are declared static + @Container so they are shared across all
 * test classes in the same JVM run (Testcontainers reuse semantics).
 * DynamicPropertySource wires the real container ports into Spring
 * environment, overriding application.properties at test time.
 *
 * Container lifecycle:
 *   - Kafka:  confluentinc/cp-kafka:7.6.1 (real broker, not EmbeddedKafka)
 *   - MySQL:  mysql:8.0 (real relational DB with DDL auto-create)
 */
@Testcontainers
public abstract class KafkaIntegrationTestBase {

    private static final DockerImageName KAFKA_IMAGE =
            DockerImageName.parse("apache/kafka-native:3.8.0")
                    .asCompatibleSubstituteFor("confluentinc/cp-kafka");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(KAFKA_IMAGE)
                    .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0")
    )
        .withDatabaseName("foodie_order_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // Kafka — real broker address from container
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // MySQL — real container JDBC URL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",
                     () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform",
                     () -> "org.hibernate.dialect.MySQLDialect");

        // Disable service discovery — no Eureka in tests
        registry.add("spring.cloud.discovery.enabled", () -> "false");

        // Disable distributed tracing — no Jaeger in tests
        registry.add("management.tracing.enabled", () -> "false");

        // Razorpay stubs (not used in order-service but avoids env-var errors
        // if any shared config tries to resolve them)
        registry.add("RAZORPAY_KEY_ID", () -> "test_key");
        registry.add("RAZORPAY_KEY_SECRET", () -> "test_secret");
        registry.add("RAZORPAY_WEBHOOK_SECRET", () -> "test_webhook");
    }
}
