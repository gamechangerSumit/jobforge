package com.jobforge.backend.company.app;

import com.jobforge.backend.company.app.CompanyModels.AdminCompanyRow;
import com.jobforge.backend.company.app.CompanyModels.CompanyDraft;
import com.jobforge.backend.company.app.CompanyModels.CompanyView;
import com.jobforge.backend.company.app.CompanyModels.MemberView;
import com.jobforge.backend.company.app.CompanyModels.MyCompanyView;
import com.jobforge.backend.company.events.CompanyEvents;
import com.jobforge.backend.company.facade.CompanyAccessFacade.Membership;
import com.jobforge.backend.shared.api.PagedResult;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ForbiddenException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.events.DomainEvent;
import com.jobforge.backend.shared.events.EventPublisher;
import com.jobforge.backend.shared.events.EventTopics;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.storage.facade.ImageUploads;
import com.jobforge.backend.storage.facade.ObjectStorage;
import java.text.Normalizer;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company onboarding, profile and membership (API_CONTRACT 12.3) plus the admin moderation transitions.
 * A recruiter belongs to at most one company (DATABASE_SCHEMA uq_company_members_user); only OWNERs manage it.
 */
@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final ObjectStorage storage;
    private final AuditService audit;
    private final EventPublisher events;
    private final Clock clock;

    public CompanyService(CompanyRepository companies, ObjectStorage storage, AuditService audit, EventPublisher events,
            Clock clock) {
        this.companies = companies;
        this.storage = storage;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public CompanyView create(AuthenticatedUser caller, CompanyDraft draft) {
        if (caller.role() != UserRole.RECRUITER) {
            throw new ForbiddenException("Only recruiters can create a company.");
        }
        if (companies.findMembershipCompany(caller.id()).isPresent()) {
            throw new ConflictException("You already belong to a company.");
        }
        UUID id = UUID.randomUUID();
        try {
            companies.insert(id, uniqueSlug(draft.name()), draft, caller.id());
            companies.insertMember(id, caller.id(), "OWNER");
        } catch (DuplicateKeyException e) {
            throw new ConflictException("The company could not be created because of a conflicting record. Please retry.");
        }
        audit.record(new AuditEntry(AuditAction.COMPANY_CREATED, "Company", id, caller.id(), caller.role(),
                AuditOutcome.SUCCESS, null, Map.of("verificationStatus", "PENDING"), null));
        return companies.findById(id).orElseThrow();
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String slug = base;
        while (companies.slugExists(slug)) {
            slug = base + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        }
        return slug;
    }

    static String slugify(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "company";
        }
        return slug.length() > 140 ? slug.substring(0, 140).replaceAll("-+$", "") : slug;
    }

    // -------------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public CompanyView get(AuthenticatedUser caller, UUID id) {
        CompanyView company = companies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company not found."));
        if (company.verified()) {
            return isMember(caller, id) || isAdmin(caller) ? company : company.publicView();
        }
        // Non-verified companies are visible only to their members and admins; everyone else gets 404.
        if (isAdmin(caller) || isMember(caller, id)) {
            return company;
        }
        throw new ResourceNotFoundException("Company not found.");
    }

    @Transactional(readOnly = true)
    public MyCompanyView mine(AuthenticatedUser caller) {
        Membership membership = companies.findMembershipCompany(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("You do not belong to a company yet."));
        CompanyView company = companies.findById(membership.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company not found."));
        return new MyCompanyView(company, membership.memberRole(), companies.members(company.id()));
    }

    @Transactional(readOnly = true)
    public PagedResult<CompanyView> listVerified(String q, int page, int size) {
        return new PagedResult<>(
                companies.listVerified(q, size, page * size).stream().map(CompanyView::publicView).toList(),
                companies.countVerified(q));
    }

    // ------------------------------------------------------------------ update

    @Transactional
    public CompanyView update(AuthenticatedUser caller, UUID id, CompanyDraft patch, Long ifMatch) {
        requireOwner(caller, id);
        CompanyView current = companies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company not found."));
        CompanyDraft merged = new CompanyDraft(or(patch.name(), current.name()),
                or(patch.description(), current.description()), or(patch.industry(), current.industry()),
                or(patch.sizeBand(), current.sizeBand()), or(patch.websiteUrl(), current.websiteUrl()),
                or(patch.hqCity(), current.hqCity()), or(patch.hqState(), current.hqState()),
                or(patch.hqCountry(), current.hqCountry()), or(patch.foundedYear(), current.foundedYear()));
        long expected = ifMatch != null ? ifMatch : current.version();
        if (companies.update(id, merged, expected).isEmpty()) {
            throw new ConflictException(ErrorCode.STALE_VERSION, "The company was modified by someone else. Reload and retry.");
        }
        audit.record(new AuditEntry(AuditAction.COMPANY_UPDATED, "Company", id, caller.id(), caller.role(),
                AuditOutcome.SUCCESS, null, null, null));
        return companies.findById(id).orElseThrow();
    }

    // -------------------------------------------------------------------- logo

    public record LogoContent(byte[] bytes, String contentType) {}

    /** OWNER only. A new object key per upload (cache busting); the previous object is removed best-effort. */
    @Transactional
    public CompanyView uploadLogo(AuthenticatedUser caller, UUID id, byte[] content) {
        requireOwner(caller, id);
        ImageUploads.Type type = ImageUploads.verify(content);
        String previous = companies.findLogoKey(id).orElse(null);
        String key = "company-logos/" + id + "/" + UUID.randomUUID() + "." + type.extension();
        storage.store(key, content);
        companies.setLogoKey(id, key);
        if (previous != null) {
            try {
                storage.delete(previous);
            } catch (RuntimeException ignored) {
                // orphaned object is harmless
            }
        }
        audit.record(AuditEntry.success(AuditAction.COMPANY_LOGO_CHANGED, "Company", id, caller.id(), caller.role()));
        return companies.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public LogoContent loadLogo(UUID id) {
        String key = companies.findLogoKey(id).orElseThrow(() -> new ResourceNotFoundException("Logo not found."));
        return new LogoContent(storage.load(key), ImageUploads.Type.fromExtension(key).contentType());
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

    // ----------------------------------------------------------------- members

    @Transactional
    public List<MemberView> addMember(AuthenticatedUser caller, UUID companyId, String email) {
        requireOwner(caller, companyId);
        UUID userId = companies.findUserIdByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("No recruiter account exists for that email."));
        if (!"RECRUITER".equals(companies.userRole(userId).orElse(null))) {
            throw new BusinessRuleException("Only recruiter accounts can be added to a company.");
        }
        if (companies.findMembershipCompany(userId).isPresent()) {
            throw new ConflictException("That recruiter already belongs to a company.");
        }
        try {
            companies.insertMember(companyId, userId, "MEMBER");
        } catch (DuplicateKeyException e) {
            throw new ConflictException("That recruiter already belongs to a company.");
        }
        audit.record(new AuditEntry(AuditAction.COMPANY_MEMBER_ADDED, "Company", companyId, caller.id(),
                caller.role(), AuditOutcome.SUCCESS, null, Map.of("memberUserId", userId.toString()), null));
        return companies.members(companyId);
    }

    @Transactional
    public void removeMember(AuthenticatedUser caller, UUID companyId, UUID userId) {
        requireOwner(caller, companyId);
        Membership target = companies.findMembershipCompany(userId).filter(m -> m.companyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found."));
        if ("OWNER".equals(target.memberRole())) {
            throw new BusinessRuleException("The company owner cannot be removed.");
        }
        companies.removeMember(companyId, userId);
        audit.record(new AuditEntry(AuditAction.COMPANY_MEMBER_REMOVED, "Company", companyId, caller.id(),
                caller.role(), AuditOutcome.SUCCESS, null, Map.of("memberUserId", userId.toString()), null));
    }

    private boolean isMember(AuthenticatedUser caller, UUID companyId) {
        return caller != null && companies.findMembershipCompany(caller.id())
                .filter(m -> m.companyId().equals(companyId)).isPresent();
    }

    private static boolean isAdmin(AuthenticatedUser caller) {
        return caller != null && caller.role() == UserRole.ADMIN;
    }

    /** Non-members get 404 (not-visible policy); members who are not owners get 403. */
    private void requireOwner(AuthenticatedUser caller, UUID companyId) {
        Membership m = companies.findMembershipCompany(caller.id()).filter(x -> x.companyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Company not found."));
        if (!"OWNER".equals(m.memberRole())) {
            throw new ForbiddenException("Only the company owner can do this.");
        }
    }

    // ------------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public PagedResult<AdminCompanyRow> adminList(String status, int page, int size) {
        return new PagedResult<>(companies.adminList(status, size, page * size), companies.adminCount(status));
    }

    @Transactional
    public void moderate(AuthenticatedUser admin, UUID companyId, String newStatus, String reason) {
        if (!List.of("VERIFIED", "REJECTED", "SUSPENDED").contains(newStatus)) {
            throw new IllegalArgumentException("Unsupported moderation status " + newStatus);
        }
        Optional<String> previous = companies.setVerification(companyId, newStatus, admin.id(), reason, clock.instant());
        if (previous.isEmpty()) {
            throw new ResourceNotFoundException("Company not found.");
        }
        String action = switch (newStatus) {
            case "VERIFIED" -> AuditAction.COMPANY_VERIFIED;
            case "REJECTED" -> AuditAction.COMPANY_REJECTED;
            default -> AuditAction.COMPANY_SUSPENDED;
        };
        audit.record(new AuditEntry(action, "Company", companyId, admin.id(), admin.role(), AuditOutcome.SUCCESS,
                Map.of("verificationStatus", previous.get()), Map.of("verificationStatus", newStatus), null));
        if ("VERIFIED".equals(newStatus)) {
            events.publish(new DomainEvent(EventTopics.USERS, "CompanyVerified", "Company", companyId, admin.id(),
                    admin.role(), new CompanyEvents.CompanyVerified(companyId, companies.ownerOf(companyId).orElse(null), admin.id())));
        } else if ("REJECTED".equals(newStatus)) {
            events.publish(new DomainEvent(EventTopics.USERS, "CompanyRejected", "Company", companyId, admin.id(),
                    admin.role(), new CompanyEvents.CompanyRejected(companyId, companies.ownerOf(companyId).orElse(null), admin.id())));
        }
    }
}
