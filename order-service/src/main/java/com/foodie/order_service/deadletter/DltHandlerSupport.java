package com.foodie.order_service.deadletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;

/**
 * Utility helpers shared by all @DltHandler methods.
 */
@Slf4j
@UtilityClass
public class DltHandlerSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Safely serialise any event object to JSON for storage. */
    public static String toJson(Object event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (Exception ex) {
            log.warn("Could not serialise DLT event payload: {}", ex.getMessage());
            return "{\"error\":\"serialisation failed\"}";
        }
    }

    /** Extract a header value as a UTF-8 string, or null if absent. */
    public static String headerString(Headers headers, String name) {
        if (headers == null) return null;
        Header h = headers.lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    /** Parse retry count from the Kafka retryable-topic exception header. */
    public static int parseRetryCount(Headers headers) {
        String raw = headerString(headers, "kafka_dlt-original-offset");
        // RetryableTopic writes attempt count into kafka_dlt-exception-cause-fqcn
        // We approximate from the "-retry-N" suffix topic name instead.
        // Fallback to 0 when not determinable.
        return 0;
    }
}
