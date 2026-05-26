package com.foodie.order_service.resilience;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Resilient wrapper around Kafka publish in order-service.
 *
 * <p>Without protection, a Kafka broker spike (high latency, partition
 * unavailability) directly blocks the {@code createOrder} HTTP handler thread.
 * The thread pool fills up, the service stops serving requests, and the user
 * sees a 30-second timeout instead of a fast error.
 *
 * <p>This wrapper applies:
 * <ul>
 *   <li><b>CircuitBreaker</b> — after 50% failure rate, stop attempting
 *       Kafka publishes.  The fallback throws {@link KafkaUnavailableException}
 *       which {@code OrderService.createOrder} catches and handles by saving
 *       the order to DB (the source of truth) and returning a degraded response.
 *       The order can be re-published later via an outbox/scheduler pattern.</li>
 *   <li><b>Retry</b> — 3 attempts with exponential backoff (500ms, 1s, 2s)
 *       to handle transient broker hiccups without immediately opening the
 *       circuit.</li>
 * </ul>
 *
 * <p>Note: the {@code @Bulkhead} for order creation is declared at the
 * {@code OrderController} level (see application.properties
 * {@code bulkhead.instances.orderCreation}) via the Spring AOP annotation
 * on the controller method — not here — to bound the entire create flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Thrown when the Kafka circuit is OPEN. */
    public static class KafkaUnavailableException extends RuntimeException {
        public KafkaUnavailableException(String msg) { super(msg); }
        public KafkaUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    /**
     * Publish a message to Kafka, protected by CircuitBreaker and Retry.
     *
     * @param topic   Kafka topic
     * @param key     message key (typically orderUuid)
     * @param payload the event object
     * @throws KafkaUnavailableException if the circuit is open or retries exhausted
     */
    @CircuitBreaker(name = "kafkaPublish", fallbackMethod = "publishFallback")
    @Retry(name = "kafkaPublish", fallbackMethod = "publishFallback")
    public void publish(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Propagate so Retry / CircuitBreaker can record the failure
                        throw new RuntimeException("Kafka send failed for topic=" + topic
                                + " key=" + key, ex);
                    } else {
                        log.debug("Kafka message sent to topic={} key={} offset={}",
                                  topic, key,
                                  result.getRecordMetadata().offset());
                    }
                });
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    /** CircuitBreaker fallback — circuit is OPEN */
    public void publishFallback(
            String topic, String key, Object payload,
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("Kafka circuit OPEN — skipping publish to topic={} key={}", topic, key);
        throw new KafkaUnavailableException(
                "Kafka is temporarily unavailable (circuit open). " +
                "Order saved; event will be re-published.", ex);
    }

    /** Retry-exhausted / generic fallback */
    public void publishFallback(
            String topic, String key, Object payload, Throwable ex) {
        log.error("Kafka publish failed after retries — topic={} key={}: {}",
                  topic, key, ex.getMessage());
        throw new KafkaUnavailableException(
                "Kafka publish failed after retries. " +
                "Order saved; event will be re-published.", ex);
    }
}
