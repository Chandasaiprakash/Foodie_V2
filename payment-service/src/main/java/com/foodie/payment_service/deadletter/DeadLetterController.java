package com.foodie.payment_service.deadletter;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** Internal operator API for dead-letter management in payment-service. */
@RestController
@RequestMapping("/internal/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    @GetMapping
    public List<DeadLetter> listAll() { return deadLetterService.listAll(); }

    @GetMapping("/pending")
    public List<DeadLetter> listPending() { return deadLetterService.listPending(); }

    @PostMapping("/{id}/replay")
    public ResponseEntity<Map<String, Object>> replay(@PathVariable Long id) {
        try {
            boolean attempted = deadLetterService.replay(id);
            return attempted
                ? ResponseEntity.ok(Map.of("success", true, "message", "Replay submitted"))
                : ResponseEntity.badRequest().body(Map.of("success", false, "message", "Not replayable"));
        } catch (RuntimeException ex) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/ignore")
    public ResponseEntity<Map<String, Object>> ignore(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "Operator suppressed") String reason) {
        deadLetterService.ignore(id, reason);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
