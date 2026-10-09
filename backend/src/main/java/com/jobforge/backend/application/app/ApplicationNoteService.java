package com.jobforge.backend.application.app;

import com.jobforge.backend.application.app.ApplicationViews.NoteView;
import com.jobforge.backend.application.domain.ApplicationNote;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.text.MarkdownSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal recruiter notes (RC-3). Visible only to recruiters of the job's company; edit/delete by the author only. */
@Service
public class ApplicationNoteService {

    private final ApplicationNoteRepository notes;
    private final ApplicationAccess access;
    private final ApplicationViewAssembler views;
    private final Clock clock;

    public ApplicationNoteService(ApplicationNoteRepository notes, ApplicationAccess access, ApplicationViewAssembler views, Clock clock) {
        this.notes = notes;
        this.access = access;
        this.views = views;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NoteView> list(AuthenticatedUser caller, UUID applicationId) {
        access.requireRecruiterAccess(caller, applicationId);
        return notes.listByApplication(applicationId).stream().map(this::toView).toList();
    }

    @Transactional
    public NoteView add(AuthenticatedUser caller, UUID applicationId, String body) {
        access.requireRecruiterAccess(caller, applicationId);
        Instant now = clock.instant();
        ApplicationNote note = new ApplicationNote(UUID.randomUUID(), applicationId, caller.id(), clean(body), now, now);
        notes.insert(note, now);
        return notes.findById(applicationId, note.id()).map(this::toView).orElseThrow();
    }

    @Transactional
    public NoteView update(AuthenticatedUser caller, UUID applicationId, UUID noteId, String body) {
        ApplicationNote note = loadOwn(caller, applicationId, noteId);
        notes.updateBody(note.id(), clean(body), clock.instant());
        return notes.findById(applicationId, noteId).map(this::toView).orElseThrow();
    }

    @Transactional
    public void delete(AuthenticatedUser caller, UUID applicationId, UUID noteId) {
        ApplicationNote note = loadOwn(caller, applicationId, noteId);
        notes.softDelete(note.id(), clock.instant());
    }

    private ApplicationNote loadOwn(AuthenticatedUser caller, UUID applicationId, UUID noteId) {
        access.requireRecruiterAccess(caller, applicationId);
        ApplicationNote note = notes.findById(applicationId, noteId)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found."));
        if (!note.authorUserId().equals(caller.id())) {
            throw new ForbiddenException("Only the author can change this note.");
        }
        return note;
    }

    private static String clean(String body) {
        String cleaned = MarkdownSanitizer.strip(body);
        if (cleaned == null || cleaned.isBlank()) {
            throw ValidationFailedException.of("body", "NOT_BLANK", "must not be blank");
        }
        return cleaned;
    }

    private NoteView toView(ApplicationNote n) {
        return new NoteView(n.id(), n.applicationId(), views.summary(n.authorUserId()), n.body(), n.createdAt(), n.updatedAt());
    }
}
