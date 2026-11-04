package com.jobforge.backend.notification.infra;

import com.jobforge.backend.shared.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Authenticated SSE transport endpoint; kept in infra so HTTP types never leak into app services. */
@RestController
@RequestMapping("/notifications")
public class NotificationStreamController {
    private final NotificationSseRegistry streams;

    public NotificationStreamController(NotificationSseRegistry streams) {
        this.streams = streams;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser caller) {
        return streams.subscribe(caller.id());
    }
}