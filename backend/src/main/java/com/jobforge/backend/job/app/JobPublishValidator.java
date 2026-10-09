package com.jobforge.backend.job.app;

import com.jobforge.backend.job.domain.JobContent;
import com.jobforge.backend.job.domain.WorkMode;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Publish validation (API_CONTRACT §12.4): local rules only, never depends on ai-service. */
public final class JobPublishValidator {

    private JobPublishValidator() {}

    public static List<FieldErrorDetail> problems(JobContent c, Instant now) {
        List<FieldErrorDetail> problems = new ArrayList<>();
        if (c.title() == null || c.title().trim().length() < 3) {
            problems.add(new FieldErrorDetail("title", "SIZE", "must be at least 3 characters"));
        }
        if (c.description() == null || c.description().trim().length() < 50) {
            problems.add(new FieldErrorDetail("description", "SIZE", "must be at least 50 characters"));
        }
        boolean locationMissing = c.location() == null || c.location().country() == null;
        if (c.workMode() != WorkMode.REMOTE && locationMissing) {
            problems.add(new FieldErrorDetail("location", "NOT_BLANK", "is required unless the job is REMOTE"));
        }
        if (c.skills() == null || c.skills().isEmpty()) {
            problems.add(new FieldErrorDetail("skills", "SIZE", "at least one skill is required"));
        } else if (c.skills().size() > 20) {
            problems.add(new FieldErrorDetail("skills", "SIZE", "at most 20 skills are allowed"));
        }
        if (c.expiresAt() == null || !c.expiresAt().isAfter(now)) {
            problems.add(new FieldErrorDetail("expiresAt", "MIN", "must be in the future"));
        }
        return problems;
    }

    /** @throws BusinessRuleException 422 with {@code details} when the content is not publishable */
    public static void require(JobContent content, Instant now) {
        List<FieldErrorDetail> problems = problems(content, now);
        if (!problems.isEmpty()) {
            throw new BusinessRuleException("The job cannot be published until the listed problems are fixed.", problems);
        }
    }
}
