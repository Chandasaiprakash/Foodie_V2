package com.foodie.notification_service.deadletter;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Internal operator API for dead-letter management in notification-service. */
@RestController
@RequestMapping("/internal/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    @GetMapping
    public List<DeadLetterEntry> listAll() { return deadLetterService.listAll(); }

    @GetMapping("/pending")
    public List<DeadLetterEntry> listPending() { return deadLetterService.listPending(); }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, Object>> replay(@PathVariable String id) {
        try {
            boolean ok = deadLetterService.replay(id);
            return ok ? ResponseEntity.ok(Map.of("success", true, "message", "Replay submitted"))
                      : ResponseEntity.badRequest().body(Map.of("success", false, "message", "Not replayable"));
        } catch (RuntimeException ex) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<Map<String, Object>> ignore(@PathVariable String id,
                                                      @RequestParam(defaultValue = "Operator suppressed") String reason) {
        deadLetterService.ignore(id, reason);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
