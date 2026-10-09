package com.jobforge.backend.user.app;

import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.user.app.UserDirectory.AdminUserRow;
import com.jobforge.backend.user.app.UserDirectory.PublicCard;
import com.jobforge.backend.user.app.UserDirectory.UserSummary;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDirectoryService {

    private static final int SEARCH_LIMIT = 10;

    private final UserDirectory.Repository repo;
    private final AuditService audit;
    private final Clock clock;

    public UserDirectoryService(UserDirectory.Repository repo, AuditService audit, Clock clock) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
    }

    static String like(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    @Transactional(readOnly = true)
    public List<UserSummary> search(String q) {
        String pattern = like(q);
        return pattern == null || q.trim().length() < 2 ? List.of() : repo.searchActive(pattern, SEARCH_LIMIT);
    }

    @Transactional(readOnly = true)
    public PublicCard publicCard(UUID id) {
        return repo.publicCard(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminUserRow> adminSearch(String q, String role, String status, int page, int size) {
        String pattern = like(q);
        return new PagedResult<>(repo.adminSearch(pattern, role, status, size, page * size),
                repo.adminCount(pattern, role, status));
    }

    @Transactional(readOnly = true)
    public AdminUserRow adminDetail(UUID id) {
        return repo.adminFind(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    @Transactional
    public AdminUserRow changeStatus(AuthenticatedUser admin, UUID id, String newStatus, String reason) {
        if (admin.id().equals(id)) {
            throw new BusinessRuleException("You cannot change your own account status.");
        }
        AdminUserRow current = adminDetail(id);
        if (!List.of("ACTIVE", "SUSPENDED").contains(current.status())) {
            throw new BusinessRuleException("Only active or suspended accounts can be changed.");
        }
        if ("ADMIN".equals(current.role()) && "SUSPENDED".equals(newStatus)) {
            throw new BusinessRuleException("Administrator accounts cannot be suspended.");
        }
        repo.setStatus(id, newStatus, clock.instant());
        audit.record(new AuditEntry(AuditAction.USER_STATUS_CHANGED, "User", id, admin.id(), admin.role(),
                AuditOutcome.SUCCESS, Map.of("status", current.status()), Map.of("status", newStatus),
                Map.of("reason", reason)));
        return adminDetail(id);
    }

    @Transactional
    public void forceLogout(AuthenticatedUser admin, UUID id) {
        if (!repo.bumpTokenVersion(id, clock.instant())) {
            throw new ResourceNotFoundException("User not found.");
        }
        audit.record(AuditEntry.success("USER_FORCE_LOGOUT", "User", id, admin.id(), admin.role()));
    }
}
