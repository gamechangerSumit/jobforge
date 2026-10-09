package com.jobforge.backend.job.domain;

import static com.jobforge.backend.job.domain.JobStatus.CLOSED;
import static com.jobforge.backend.job.domain.JobStatus.DRAFT;
import static com.jobforge.backend.job.domain.JobStatus.EXPIRED;
import static com.jobforge.backend.job.domain.JobStatus.PUBLISHED;
import static com.jobforge.backend.job.domain.JobStatus.REMOVED;
import static com.jobforge.backend.job.domain.JobStatus.UNPUBLISHED;

import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.util.List;

/** Job status transitions (DATABASE_SCHEMA §3.5). Pure rules; no I/O. */
public final class JobStatusMachine {

    public enum Actor { RECRUITER, ADMIN, SYSTEM }

    private record Rule(JobStatus from, JobStatus to, Actor actor) {}

    private static final List<Rule> RULES = List.of(
            new Rule(DRAFT, PUBLISHED, Actor.RECRUITER),
            new Rule(PUBLISHED, UNPUBLISHED, Actor.RECRUITER),
            new Rule(PUBLISHED, CLOSED, Actor.RECRUITER),
            new Rule(UNPUBLISHED, PUBLISHED, Actor.RECRUITER),
            new Rule(UNPUBLISHED, CLOSED, Actor.RECRUITER),
            new Rule(CLOSED, PUBLISHED, Actor.RECRUITER),
            new Rule(EXPIRED, PUBLISHED, Actor.RECRUITER),
            new Rule(PUBLISHED, EXPIRED, Actor.SYSTEM),
            new Rule(DRAFT, REMOVED, Actor.ADMIN),
            new Rule(PUBLISHED, REMOVED, Actor.ADMIN),
            new Rule(UNPUBLISHED, REMOVED, Actor.ADMIN),
            new Rule(CLOSED, REMOVED, Actor.ADMIN),
            new Rule(EXPIRED, REMOVED, Actor.ADMIN),
            new Rule(REMOVED, UNPUBLISHED, Actor.ADMIN));

    private JobStatusMachine() {}

    public static boolean isAllowed(JobStatus from, JobStatus to, Actor actor) {
        return RULES.contains(new Rule(from, to, actor));
    }

    /** @throws ConflictException 409 INVALID_STATE_TRANSITION */
    public static void require(JobStatus from, JobStatus to, Actor actor) {
        if (!isAllowed(from, to, actor)) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A job in status " + from + " cannot move to " + to + ".");
        }
    }
}
