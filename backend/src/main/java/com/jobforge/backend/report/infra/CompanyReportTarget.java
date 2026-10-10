package com.jobforge.backend.report.infra;

import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import com.jobforge.backend.report.app.ReportTargetPort;
import com.jobforge.backend.report.domain.ReportTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * COMPANY reports. Only VERIFIED, non-deleted companies are publicly visible, so only those are reportable. A
 * soft-deleted company is treated as missing (404 on filing; "target unavailable" in the admin detail) even though its
 * verification status stays VERIFIED. Company suspension is not defined as a report outcome in the contracts;
 * supported decisions are DISMISS and WARN_USER (the owner).
 */
@Component
public class CompanyReportTarget implements ReportTargetPort {

    private final CompanyAccessFacade companies;

    public CompanyReportTarget(CompanyAccessFacade companies) {
        this.companies = companies;
    }

    @Override
    public ReportTargetType type() {
        return ReportTargetType.COMPANY;
    }

    @Override
    public Optional<TargetSnapshot> snapshot(UUID targetId) {
        // summaries() does not filter deleted_at; ownerOf() is empty for unknown or soft-deleted companies
        // (CompanyAccessFacade contract), so it is the deletion guard here. No new facade method is needed.
        Optional<UUID> owner = companies.ownerOf(targetId);
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        CompanyInfo info = companies.summaries(List.of(targetId)).get(targetId);
        if (info == null) {
            return Optional.empty();
        }
        return Optional.of(new TargetSnapshot(info.id(), info.name(), info.verified() ? "VERIFIED" : "UNVERIFIED",
                info.verified(), owner.get()));
    }
}
