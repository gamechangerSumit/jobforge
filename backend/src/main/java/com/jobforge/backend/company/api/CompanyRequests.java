package com.jobforge.backend.company.api;

import com.jobforge.backend.company.app.CompanyModels.CompanyDraft;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request DTOs; limits mirror API_CONTRACT 10 and the companies table constraints. */
public final class CompanyRequests {

    private static final String SIZE_BAND = "^(1_10|11_50|51_200|201_500|501_1000|1000_PLUS)$";

    private CompanyRequests() {}

    public record Create(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 5000) String description,
            @Size(max = 80) String industry,
            @Pattern(regexp = SIZE_BAND) String sizeBand,
            @Size(max = 255) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String websiteUrl,
            @Size(max = 80) String hqCity,
            @Size(max = 80) String hqState,
            @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String hqCountry,
            @Min(1800) @Max(2100) Integer foundedYear) {

        public CompanyDraft toDraft() {
            return new CompanyDraft(name.trim(), description, industry, sizeBand, websiteUrl, hqCity, hqState,
                    hqCountry, foundedYear);
        }
    }

    public record Patch(
            @Size(min = 1, max = 150) String name,
            @Size(max = 5000) String description,
            @Size(max = 80) String industry,
            @Pattern(regexp = SIZE_BAND) String sizeBand,
            @Size(max = 255) @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String websiteUrl,
            @Size(max = 80) String hqCity,
            @Size(max = 80) String hqState,
            @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String hqCountry,
            @Min(1800) @Max(2100) Integer foundedYear) {

        public CompanyDraft toDraft() {
            return new CompanyDraft(name == null ? null : name.trim(), description, industry, sizeBand, websiteUrl,
                    hqCity, hqState, hqCountry, foundedYear);
        }
    }

    public record AddMember(@NotBlank @Email @Size(max = 254) String email) {}

    public record Reject(@NotBlank @Size(max = 500) String reason) {}
}
