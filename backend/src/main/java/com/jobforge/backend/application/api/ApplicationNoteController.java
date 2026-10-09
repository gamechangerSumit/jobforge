package com.jobforge.backend.application.api;

import com.jobforge.backend.application.app.ApplicationNoteService;
import com.jobforge.backend.application.app.ApplicationViews.NoteView;
import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal recruiter notes on an application (RC-3). Never exposed to seekers. */
@RestController
@RequestMapping("/applications/{applicationId}/notes")
@PreAuthorize("hasRole('RECRUITER')")
public class ApplicationNoteController {

    private final ApplicationNoteService notes;

    public ApplicationNoteController(ApplicationNoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<NoteView> list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID applicationId) {
        return notes.list(caller, applicationId);
    }

    @PostMapping
    public ResponseEntity<NoteView> add(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID applicationId,
            @Valid @RequestBody ApplicationRequests.Note request) {
        NoteView created = notes.add(caller, applicationId, request.body());
        return ResponseEntity.created(URI.create(ApiPaths.BASE + "/applications/" + applicationId + "/notes/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{noteId}")
    public NoteView update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID applicationId,
            @PathVariable UUID noteId, @Valid @RequestBody ApplicationRequests.Note request) {
        return notes.update(caller, applicationId, noteId, request.body());
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID applicationId,
            @PathVariable UUID noteId) {
        notes.delete(caller, applicationId, noteId);
        return ResponseEntity.noContent().build();
    }
}
