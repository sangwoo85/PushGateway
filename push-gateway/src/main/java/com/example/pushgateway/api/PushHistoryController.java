package com.example.pushgateway.api;

import com.example.pushgateway.domain.NotificationType;
import com.example.pushgateway.repository.PushCompletionRepository;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/push/history")
public class PushHistoryController {
    private final PushCompletionRepository completions;

    public PushHistoryController(PushCompletionRepository completions) {
        this.completions = completions;
    }

    @GetMapping
    public List<PushHistoryResponse> history(
            @RequestParam(defaultValue = "50") int limit, Principal principal) {
        String userId = authenticatedUser(principal);
        int safeLimit = Math.clamp(limit, 1, 100);
        return completions.findRecentForUser(userId, safeLimit).stream()
                .map(item -> new PushHistoryResponse(item.eventId(), item.notificationType(),
                        item.actorName(), item.sentAt()))
                .toList();
    }

    private String authenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal.getName();
    }

    public record PushHistoryResponse(UUID eventId, NotificationType notificationType,
                                      String actorName, Instant sentAt) {}
}
