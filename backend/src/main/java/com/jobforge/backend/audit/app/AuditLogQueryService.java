package com.jobforge.backend.audit.app;

import com.jobforge.backend.audit.app.AuditLogs.Filter;
import com.jobforge.backend.audit.app.AuditLogs.Row;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.error.ValidationFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only query over core.audit_logs. */
@Service
public class AuditLogQueryService {

    private final AuditLogs.Repository repo;

    public AuditLogQueryService(AuditLogs.Repository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public PagedResult<Row> search(Filter filter, int page, int size) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ValidationFailedException.of("from", "INVALID_RANGE", "must not be after 'to'");
        }
        return new PagedResult<>(repo.search(filter, size, page * size), repo.count(filter));
    }
}
