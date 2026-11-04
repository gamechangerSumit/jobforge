package com.jobforge.backend.notification.api;

import com.jobforge.backend.notification.app.NotificationPreference;
import com.jobforge.backend.notification.app.NotificationResponse;
import com.jobforge.backend.notification.app.NotificationService;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated notification inbox, preferences and live stream (API_CONTRACT §12.8). */
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public CursorResponse<NotificationResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return service.find(caller.id(), unread, cursor, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser caller) {
        return Map.of("count", service.unreadCount(caller.id()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        service.markRead(caller.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AuthenticatedUser caller) {
        service.markAllRead(caller.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public List<NotificationPreference> preferences(@AuthenticationPrincipal AuthenticatedUser caller) {
        return service.preferences(caller.id());
    }

    @PutMapping("/preferences")
    public List<NotificationPreference> updatePreferences(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestBody List<NotificationPreference> preferences) {
        return service.updatePreferences(caller.id(), preferences);
    }

}