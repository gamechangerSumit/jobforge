package com.jobforge.backend.company.api;

import com.jobforge.backend.company.app.CompanyModels.AdminCompanyRow;
import com.jobforge.backend.company.app.CompanyService;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.error.ValidationFailedException;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.QueryParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API_CONTRACT 12.14 — admin company moderation (ADMIN only; URL rule + method security). */
@RestController
@RequestMapping("/admin/companies")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCompanyController {

    private static final List<String> STATUSES = List.of("PENDING", "VERIFIED", "REJECTED", "SUSPENDED");

    private final CompanyService companies;

    public AdminCompanyController(CompanyService companies) {
        this.companies = companies;
    }

    @GetMapping
    public PagedResponse<AdminCompanyRow> list(HttpServletRequest request) {
        QueryParams params = new QueryParams(request, "status", "page", "size");
        String status = params.string("status");
        if (status != null && !STATUSES.contains(status)) {
            throw ValidationFailedException.of("status", "INVALID_ENUM", "must be one of the allowed values");
        }
        int page = params.page();
        int size = params.size();
        return companies.adminList(status, page, size).toResponse(Function.identity(), page, size);
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id) {
        companies.moderate(admin, id, "VERIFIED", null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
            @Valid @RequestBody CompanyRequests.Reject request) {
        companies.moderate(admin, id, "REJECTED", request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
            @Valid @RequestBody CompanyRequests.Reject request) {
        companies.moderate(admin, id, "SUSPENDED", request.reason());
        return ResponseEntity.noContent().build();
    }
}
