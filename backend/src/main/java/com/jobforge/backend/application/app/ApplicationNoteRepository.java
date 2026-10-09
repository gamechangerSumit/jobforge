package com.jobforge.backend.application.app;

import com.jobforge.backend.application.domain.ApplicationNote;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationNoteRepository {

    void insert(ApplicationNote note, Instant now);

    List<ApplicationNote> listByApplication(UUID applicationId);

    Optional<ApplicationNote> findById(UUID applicationId, UUID noteId);

    void updateBody(UUID noteId, String body, Instant now);

    void softDelete(UUID noteId, Instant now);
}
