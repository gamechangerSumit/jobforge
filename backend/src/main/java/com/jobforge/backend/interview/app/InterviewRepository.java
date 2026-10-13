package com.jobforge.backend.interview.app;

import com.jobforge.backend.interview.domain.Interview;
import com.jobforge.backend.interview.domain.InterviewStatus;
import com.jobforge.backend.shared.api.PagedResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port of the interview module (implemented in {@code infra}). */
public interface InterviewRepository {

    void insert(Interview interview, Instant now);

    Optional<Interview> findById(UUID id);

    /**
     * Writes every mutable column of {@code next}, but only while the stored status is still {@code expectedStatus}
     * and the row is still at {@code next.updatedAt()} (the value that was read; the table has no version column, so
     * {@code updated_at} acts as the optimistic lock and also stops lost updates between two recruiters).
     * {@code next} must therefore be derived from a row that was just read.
     *
     * @return false when the interview changed in the meantime
     */
    boolean update(Interview next, InterviewStatus expectedStatus, Instant now);

    /** Interviews of the application that are not final (SCHEDULED, CONFIRMED, DECLINED). */
    List<Interview> findOpenByApplication(UUID applicationId);

    /**
     * True when an active (SCHEDULED/CONFIRMED) interview of the application overlaps {@code [start, end)}.
     * Must be called inside the transaction that then writes: it first takes a per-application transaction-scoped lock,
     * so concurrent schedule/reschedule calls for one application are checked one after the other.
     */
    boolean existsActiveOverlap(UUID applicationId, Instant start, Instant end, UUID excludeInterviewId);

    /** Interviews of the given applications, earliest first. {@code from}/{@code to} bound {@code scheduled_at}. */
    PagedResult<Interview> search(Collection<UUID> applicationIds, InterviewStatus status, Instant from, Instant to, int page,
            int size);
}
