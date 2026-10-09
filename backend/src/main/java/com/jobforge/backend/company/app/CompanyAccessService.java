package com.jobforge.backend.company.app;

import com.jobforge.backend.company.facade.CompanyAccessFacade;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyAccessService implements CompanyAccessFacade {

    private final CompanyAccessRepository companies;
    private final CompanyRepository companyRepository;

    public CompanyAccessService(CompanyAccessRepository companies, CompanyRepository companyRepository) {
        this.companies = companies;
        this.companyRepository = companyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> membershipOf(UUID userId) {
        return companies.findMembership(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CompanyInfo> summaries(Collection<UUID> companyIds) {
        return companyIds.isEmpty() ? Map.of() : companies.findSummaries(companyIds);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVerified(UUID companyId) {
        return companies.isVerified(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> accountDeletionBlocker(UUID userId) {
        Optional<Membership> membership = companies.findMembership(userId);
        if (membership.isEmpty() || !"OWNER".equals(membership.get().memberRole())) {
            return Optional.empty();
        }
        UUID companyId = membership.get().companyId();
        if (companyRepository.members(companyId).size() > 1) {
            return Optional.of("You own a company that still has other members. Remove them before deleting your account.");
        }
        boolean hasOpenJobs = companyRepository.findById(companyId).map(c -> c.openJobCount() > 0).orElse(false);
        if (hasOpenJobs) {
            return Optional.of("Your company still has open jobs. Close them before deleting your account.");
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public void releaseMembership(UUID userId) {
        companies.findMembership(userId).ifPresent(m -> companyRepository.removeMember(m.companyId(), userId));
    }
}
