package com.foodie.order_service.deadletter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal operator API for dead-letter management.
 *
 * <p>Endpoints are intentionally placed under {@code /internal/dead-letters}
 * so they can be blocked at the gateway (same pattern as InternalUserController).
 * Only ops tooling / admin dashboards should reach these.
 *
 * <pre>
 *   GET  /internal/dead-letters          — list all dead letters
 *   GET  /internal/dead-letters/pending  — list PENDING only
 *   POST /internal/dead-letters/{id}/replay  — replay a message
 *   POST /internal/dead-letters/{id}/ignore  — mark as IGNORED
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/internal/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    @GetMapping
    public List<DeadLetter> listAll() {
        return deadLetterService.listAll();
    }

    @GetMapping("/pending")
    public List<DeadLetter> listPending() {
        return deadLetterService.listPending();
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, Object>> replay(@PathVariable Long id) {
        try {
            boolean attempted = deadLetterService.replay(id);
            if (!attempted) {
                return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Dead letter is not in a replayable state"));
            }
            return ResponseEntity.ok(Map.of("success", true, "message", "Replay submitted"));
        } catch (RuntimeException ex) {
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<Map<String, Object>> ignore(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "Operator suppressed") String reason) {
        deadLetterService.ignore(id, reason);
        return ResponseEntity.ok(Map.of("success", true, "message", "Marked as IGNORED"));
    }
}
