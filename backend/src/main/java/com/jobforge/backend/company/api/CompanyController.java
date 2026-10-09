package com.jobforge.backend.company.api;

import com.jobforge.backend.company.app.CompanyModels.CompanyView;
import com.jobforge.backend.company.app.CompanyModels.MemberView;
import com.jobforge.backend.company.app.CompanyModels.MyCompanyView;
import com.jobforge.backend.company.app.CompanyService;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.IfMatch;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.3 — company onboarding, public profile and membership. */
@RestController
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companies;

    public CompanyController(CompanyService companies) {
        this.companies = companies;
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<CompanyView> create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CompanyRequests.Create request) {
        CompanyView created = companies.create(caller, request.toDraft());
        return ResponseEntity.created(URI.create("/api/v1/companies/" + created.id()))
                .eTag("\"" + created.version() + "\"").body(created);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('RECRUITER')")
    public MyCompanyView mine(@AuthenticationPrincipal AuthenticatedUser caller) {
        return companies.mine(caller);
    }

    @GetMapping
    public PagedResponse<CompanyView> list(HttpServletRequest request) {
        QueryParams params = new QueryParams(request, "q", "page", "size");
        int page = params.page();
        int size = params.size();
        return companies.listVerified(params.string("q"), page, size).toResponse(Function.identity(), page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyView> get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        CompanyView view = companies.get(caller, id);
        return ResponseEntity.ok().eTag("\"" + view.version() + "\"").body(view);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<CompanyView> update(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody CompanyRequests.Patch request) {
        CompanyView updated = companies.update(caller, id, request.toDraft(), IfMatch.parse(ifMatch));
        return ResponseEntity.ok().eTag("\"" + updated.version() + "\"").body(updated);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<List<MemberView>> addMember(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody CompanyRequests.AddMember request) {
        return ResponseEntity.status(201).body(companies.addMember(caller, id, request.email()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @PathVariable UUID userId) {
        companies.removeMember(caller, id, userId);
        return ResponseEntity.noContent().build();
    }

    /** Owner uploads a logo (PNG/JPEG/WEBP, max 2 MB, magic bytes verified). */
    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<CompanyView> uploadLogo(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "The file could not be read.");
        }
        CompanyView updated = companies.uploadLogo(caller, id, bytes);
        return ResponseEntity.ok().eTag("\"" + updated.version() + "\"").body(updated);
    }

    /** Public (see CompanySecurityRulesContributor) so img tags work without the bearer token. */
    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID id) {
        CompanyService.LogoContent logo = companies.loadLogo(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(logo.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(logo.bytes());
    }
}
