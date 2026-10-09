package com.jobforge.backend.application.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.application.app.ApplicationViews.ApplicationView;
import com.jobforge.backend.application.app.ApplicationViews.HistoryItem;
import com.jobforge.backend.application.app.ApplicationViews.UserSummary;
import com.jobforge.backend.application.domain.Application;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.job.facade.JobViews.JobLiteView;
import com.jobforge.backend.user.facade.UserAccountView;
import com.jobforge.backend.user.facade.UserFacade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds application views. Seeker view omits recruiter-only data (rating, seeker, snapshot); recruiter/admin see all. */
@Component
public class ApplicationViewAssembler {

    private final ApplicationRepository applications;
    private final JobFacade jobs;
    private final UserFacade users;
    private final ObjectMapper mapper;

    public ApplicationViewAssembler(ApplicationRepository applications, JobFacade jobs, UserFacade users, ObjectMapper mapper) {
        this.applications = applications;
        this.jobs = jobs;
        this.users = users;
        this.mapper = mapper;
    }

    public ApplicationView seekerView(Application a, boolean withHistory) {
        JobLiteView job = jobs.getLites(List.of(a.jobId())).get(a.jobId());
        return build(a, job, null, false, withHistory);
    }

    public ApplicationView recruiterView(Application a, boolean withHistory) {
        JobLiteView job = jobs.getLites(List.of(a.jobId())).get(a.jobId());
        return build(a, job, summary(a.seekerUserId()), true, withHistory);
    }

    /** List rendering: batched job lookups, no history. */
    public List<ApplicationView> seekerList(List<Application> items) {
        Map<UUID, JobLiteView> lites = jobs.getLites(items.stream().map(Application::jobId).distinct().toList());
        return items.stream().map(a -> build(a, lites.get(a.jobId()), null, false, false)).toList();
    }

    public List<ApplicationView> recruiterList(List<Application> items) {
        Map<UUID, JobLiteView> lites = jobs.getLites(items.stream().map(Application::jobId).distinct().toList());
        Map<UUID, UserSummary> seekers = new HashMap<>();
        items.forEach(a -> seekers.computeIfAbsent(a.seekerUserId(), this::summary));
        return items.stream().map(a -> build(a, lites.get(a.jobId()), seekers.get(a.seekerUserId()), true, false)).toList();
    }

    public UserSummary summary(UUID userId) {
        return users.findById(userId).map(ApplicationViewAssembler::toSummary).orElse(null);
    }

    public static UserSummary toSummary(UserAccountView u) {
        return new UserSummary(u.id(), u.handle(), u.firstName(), u.lastName(), null, u.role());
    }

    private ApplicationView build(Application a, JobLiteView job, UserSummary seeker, boolean recruiterSide, boolean withHistory) {
        List<HistoryItem> history = withHistory
                ? applications.history(a.id()).stream()
                        .map(h -> new HistoryItem(h.from() == null ? null : h.from().name(), h.to().name(), h.changedBy(),
                                h.reason(), h.createdAt()))
                        .toList()
                : null;
        return new ApplicationView(a.id(), a.jobId(), job, seeker, a.status().name(), a.coverLetter(), a.resumeId(),
                recruiterSide ? a.rating() : null, a.appliedAt(), a.statusUpdatedAt(), history,
                recruiterSide ? snapshot(a.profileSnapshot()) : null, a.version());
    }

    private JsonNode snapshot(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
