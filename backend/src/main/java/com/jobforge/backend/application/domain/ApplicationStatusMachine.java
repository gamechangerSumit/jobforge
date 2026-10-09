package com.jobforge.backend.application.domain;

import static com.jobforge.backend.application.domain.ApplicationStatus.HIRED;
import static com.jobforge.backend.application.domain.ApplicationStatus.INTERVIEW;
import static com.jobforge.backend.application.domain.ApplicationStatus.OFFERED;
import static com.jobforge.backend.application.domain.ApplicationStatus.REJECTED;
import static com.jobforge.backend.application.domain.ApplicationStatus.SHORTLISTED;
import static com.jobforge.backend.application.domain.ApplicationStatus.SUBMITTED;
import static com.jobforge.backend.application.domain.ApplicationStatus.UNDER_REVIEW;
import static com.jobforge.backend.application.domain.ApplicationStatus.WITHDRAWN;

import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.util.List;

/** Application transition matrix (DATABASE_SCHEMA §3.6). Recruiters move forward/reject; only seekers withdraw. */
public final class ApplicationStatusMachine {

    public enum Actor { RECRUITER, SEEKER }

    private record Rule(ApplicationStatus from, ApplicationStatus to, Actor actor) {}

    private static final List<Rule> RULES = List.of(
            new Rule(SUBMITTED, UNDER_REVIEW, Actor.RECRUITER),
            new Rule(SUBMITTED, REJECTED, Actor.RECRUITER),
            new Rule(UNDER_REVIEW, SHORTLISTED, Actor.RECRUITER),
            new Rule(UNDER_REVIEW, REJECTED, Actor.RECRUITER),
            new Rule(SHORTLISTED, INTERVIEW, Actor.RECRUITER),
            new Rule(SHORTLISTED, REJECTED, Actor.RECRUITER),
            new Rule(INTERVIEW, OFFERED, Actor.RECRUITER),
            new Rule(INTERVIEW, REJECTED, Actor.RECRUITER),
            new Rule(OFFERED, HIRED, Actor.RECRUITER),
            new Rule(OFFERED, REJECTED, Actor.RECRUITER),
            new Rule(SUBMITTED, WITHDRAWN, Actor.SEEKER),
            new Rule(UNDER_REVIEW, WITHDRAWN, Actor.SEEKER),
            new Rule(SHORTLISTED, WITHDRAWN, Actor.SEEKER),
            new Rule(INTERVIEW, WITHDRAWN, Actor.SEEKER),
            new Rule(OFFERED, WITHDRAWN, Actor.SEEKER));

    private ApplicationStatusMachine() {}

    public static boolean isAllowed(ApplicationStatus from, ApplicationStatus to, Actor actor) {
        return RULES.contains(new Rule(from, to, actor));
    }

    /** @throws ConflictException 409 INVALID_STATE_TRANSITION */
    public static void require(ApplicationStatus from, ApplicationStatus to, Actor actor) {
        if (!isAllowed(from, to, actor)) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "An application in status " + from + " cannot move to " + to + ".");
        }
    }
}
