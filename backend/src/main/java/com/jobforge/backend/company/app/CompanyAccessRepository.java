package com.jobforge.backend.company.app;

import com.jobforge.backend.company.facade.CompanyAccessFacade.CompanyInfo;
import com.jobforge.backend.company.facade.CompanyAccessFacade.Membership;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CompanyAccessRepository {

    Optional<Membership> findMembership(UUID userId);

    Map<UUID, CompanyInfo> findSummaries(Collection<UUID> companyIds);

    boolean isVerified(UUID companyId);
}
