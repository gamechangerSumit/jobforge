package com.jobforge.backend.notification.api;

import com.jobforge.backend.notification.app.NotificationResponse;
import com.jobforge.backend.notification.app.NotificationService;
import com.jobforge.backend.shared.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(
            NotificationService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> list(
            @RequestParam(
                    defaultValue = "false"
            )
            boolean unread,

            @RequestParam(
                    defaultValue = "20"
            )
            int limit
    ) {

        return service.find(
                CurrentUser.require().id(),
                unread,
                limit
        );
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {

        return Map.of(
                "count",
                service.unreadCount(
                        CurrentUser.require().id()
                )
        );
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(
            @PathVariable UUID id
    ) {

        service.markRead(
                CurrentUser.require().id(),
                id
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll() {

        service.markAllRead(
                CurrentUser.require().id()
        );

        return ResponseEntity.noContent().build();
    }
}