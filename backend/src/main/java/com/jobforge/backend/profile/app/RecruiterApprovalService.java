package com.jobforge.backend.profile.app;

import com.jobforge.backend.profile.app.RecruiterApprovalRepository.Row;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin recruiter approval (API_CONTRACT 12.14). Publishing jobs requires APPROVED (D-16). */
@Service
public class RecruiterApprovalService {

    public record RecruiterApproved(UUID userId, UUID approvedBy) {}

    public record RecruiterRejected(UUID userId, UUID rejectedBy) {}

    private final RecruiterApprovalRepository repo;
    private final AuditService audit;
    private final EventPublisher events;
    private final Clock clock;

    public RecruiterApprovalService(RecruiterApprovalRepository repo, AuditService audit, EventPublisher events,
            Clock clock) {
        this.repo = repo;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResult<Row> list(String status, int page, int size) {
        return new PagedResult<>(repo.list(status, size, page * size), repo.count(status));
    }

    @Transactional(readOnly = true)
    public RecruiterApprovalRepository.Detail get(UUID userId) {
        return repo.find(userId).orElseThrow(() -> new ResourceNotFoundException("Recruiter not found."));
    }

    @Transactional
    public void decide(AuthenticatedUser admin, UUID userId, boolean approve, String reason) {
        String status = approve ? "APPROVED" : "REJECTED";
        String previous = repo.setStatus(userId, status, admin.id(), reason, clock.instant())
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter not found."));
        audit.record(new AuditEntry(approve ? AuditAction.RECRUITER_APPROVED : AuditAction.RECRUITER_REJECTED,
                "RecruiterProfile", userId, admin.id(), admin.role(), AuditOutcome.SUCCESS,
                Map.of("approvalStatus", previous), Map.of("approvalStatus", status), null));
        Object payload = approve ? new RecruiterApproved(userId, admin.id()) : new RecruiterRejected(userId, admin.id());
        events.publish(new DomainEvent(EventTopics.USERS, approve ? "RecruiterApproved" : "RecruiterRejected",
                "User", userId, admin.id(), admin.role(), payload));
    }
}
