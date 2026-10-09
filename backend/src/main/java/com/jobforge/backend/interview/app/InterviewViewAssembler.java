package com.jobforge.backend.interview.app;

import com.jobforge.backend.application.facade.ApplicationFacade.InterviewContext;
import com.jobforge.backend.interview.app.InterviewViews.InterviewView;
import com.jobforge.backend.interview.app.InterviewViews.UserSummary;
import com.jobforge.backend.interview.domain.Interview;
import com.jobforge.backend.job.facade.JobFacade;
import com.jobforge.backend.job.facade.JobViews.JobLiteView;
import com.jobforge.backend.user.facade.UserFacade;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds interview views from already-authorised interviews. Seeker views never contain recruiter-only data. */
@Component
public class InterviewViewAssembler {

    private final JobFacade jobs;
    private final UserFacade users;

    public InterviewViewAssembler(JobFacade jobs, UserFacade users) {
        this.jobs = jobs;
        this.users = users;
    }

    public InterviewView recruiterView(Interview interview, InterviewContext context) {
        return recruiterList(List.of(interview), Map.of(interview.applicationId(), context)).get(0);
    }

    public InterviewView seekerView(Interview interview, InterviewContext context) {
        return seekerList(List.of(interview), Map.of(interview.applicationId(), context)).get(0);
    }

    public List<InterviewView> recruiterList(List<Interview> items, Map<UUID, InterviewContext> contexts) {
        return build(items, contexts, true);
    }

    public List<InterviewView> seekerList(List<Interview> items, Map<UUID, InterviewContext> contexts) {
        return build(items, contexts, false);
    }

    private List<InterviewView> build(List<Interview> items, Map<UUID, InterviewContext> contexts, boolean recruiterSide) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<UUID> jobIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (Interview i : items) {
            InterviewContext ctx = contexts.get(i.applicationId());
            if (ctx != null) {
                jobIds.add(ctx.jobId());
                if (recruiterSide) {
                    userIds.add(ctx.seekerUserId());
                }
            }
            userIds.add(i.scheduledBy());
        }
        Map<UUID, JobLiteView> lites = jobs.getLites(jobIds);
        Map<UUID, UserSummary> summaries = new HashMap<>();
        userIds.forEach(id -> users.findById(id)
                .ifPresent(u -> summaries.put(id, new UserSummary(u.id(), u.handle(), u.firstName(), u.lastName()))));
        return items.stream().map(i -> {
            InterviewContext ctx = contexts.get(i.applicationId());
            return new InterviewView(i.id(), i.applicationId(), ctx == null ? null : ctx.status(),
                    ctx == null ? null : lites.get(ctx.jobId()),
                    recruiterSide && ctx != null ? summaries.get(ctx.seekerUserId()) : null,
                    summaries.get(i.scheduledBy()), i.type().name(), i.scheduledAt(), i.durationMinutes(), i.timezone(),
                    i.locationOrLink(), i.status().name(), i.seekerResponse().name(), i.seekerResponseNote(),
                    i.cancelledReason(), recruiterSide ? i.internalNotes() : null, i.createdAt(), i.updatedAt());
        }).toList();
    }
}
