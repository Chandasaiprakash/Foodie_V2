package com.foodie.notification_service.config;

import com.foodie.common.events.OrderUpdatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Kafka consumer configuration for notification-service.
 *
 * <p>Two factories are registered:
 * <ul>
 *   <li>{@code kafkaListenerContainerFactory} — generic Object deserialiser,
 *       used by delivery-events listener and as the default for @RetryableTopic
 *       retry/DLT infrastructure topics.</li>
 *   <li>{@code orderUpdatedEventListenerFactory} — typed OrderUpdatedEvent
 *       deserialiser, used by the order-updated listener.</li>
 * </ul>
 *
 * <p>Both factories route listener dispatch to <b>virtual threads</b> via
 * {@code setListenerTaskExecutor}. Notification-service is particularly well-suited
 * for virtual threads: every listener invocation involves a Redis write (idempotency
 * claim) and a WebSocket broadcast — both I/O operations that previously blocked a
 * platform thread for their full duration.
 *
 * <p>Both factories have {@code setMissingTopicsFatal(false)} so the service
 * starts cleanly before the DLT topics are auto-created by @RetryableTopic.
 */
@Configuration
public class KafkaConsumerConfig {

    @Autowired
    private ObservationRegistry observationRegistry;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private Map<String, Object> baseProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.foodie.common.events");
        return props;
    }

    /** Shared virtual-thread executor for all listener factories in this service. */
    private org.springframework.core.task.AsyncTaskExecutor virtualThreadExecutor() {
        return new org.springframework.core.task.support.TaskExecutorAdapter(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            baseProps("notification-service-group"),
            new StringDeserializer(),
            new JsonDeserializer<>(Object.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setMissingTopicsFatal(false);
        // Enable Micrometer observation for this listener container.
        // Spring Kafka records a span for each @KafkaListener invocation.
        // Combined with OTelKafkaPropagation in the consumer aspect, this
        // produces child spans correctly linked to the producer span.
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setListenerTaskExecutor(virtualThreadExecutor());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, OrderUpdatedEvent> orderUpdatedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            baseProps("notification-service-group"),
            new StringDeserializer(),
            new JsonDeserializer<>(OrderUpdatedEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderUpdatedEvent> orderUpdatedEventListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderUpdatedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderUpdatedConsumerFactory());
        factory.setMissingTopicsFatal(false);
        // Enable Micrometer observation for this listener container.
        // Spring Kafka records a span for each @KafkaListener invocation.
        // Combined with OTelKafkaPropagation in the consumer aspect, this
        // produces child spans correctly linked to the producer span.
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setListenerTaskExecutor(virtualThreadExecutor());
        return factory;
    }
}
