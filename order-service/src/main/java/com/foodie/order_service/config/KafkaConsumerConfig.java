package com.foodie.order_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Kafka consumer configuration for order-service.
 *
 * <p>The listener container factory is configured to dispatch each consumed
 * message on a <b>virtual thread</b> via
 * {@link ConcurrentKafkaListenerContainerFactory#setListenerTaskExecutor}.
 * This means every {@code @KafkaListener} invocation (including retry-topic
 * and DLT redeliveries) runs on a lightweight virtual thread rather than a
 * pooled platform thread — removing the fixed thread-pool ceiling entirely.
 *
 * <p>The Kafka poll loop itself still runs on a platform thread owned by the
 * container (one per partition assignment). Virtual threads are used only for
 * the listener <em>dispatch</em> path, which is where I/O blocking occurs
 * (database writes, downstream HTTP calls). This is the correct and safe
 * boundary: the Kafka client internals are not modified.
 */
@Configuration
public class KafkaConsumerConfig {

    @Autowired
    private ObservationRegistry observationRegistry;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.foodie.common.events");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        JsonDeserializer<Object> valueDeserializer = new JsonDeserializer<>(Object.class, true);
        valueDeserializer.addTrustedPackages("com.foodie.common.events");

        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            valueDeserializer
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
        // Dispatch each listener invocation on a virtual thread.
        factory.getContainerProperties().setListenerTaskExecutor(
            new org.springframework.core.task.support.TaskExecutorAdapter(
                Executors.newVirtualThreadPerTaskExecutor()
            )
        );
        return factory;
    }
}
