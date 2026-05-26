package com.foodie.payment_service.config;

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
 * Kafka consumer configuration for payment-service.
 *
 * <p>Listener dispatch is routed to virtual threads via
 * {@code setListenerTaskExecutor}. See order-service {@code KafkaConsumerConfig}
 * for full design notes on the virtual-thread dispatch model.
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
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.foodie.common.events");
        return new DefaultKafkaConsumerFactory<>(
            props,
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
        factory.getContainerProperties().setListenerTaskExecutor(
            new org.springframework.core.task.support.TaskExecutorAdapter(
                Executors.newVirtualThreadPerTaskExecutor()
            )
        );
        return factory;
    }
}
