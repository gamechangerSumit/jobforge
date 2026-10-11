package com.jobforge.backend.shared.web;

import com.jobforge.backend.shared.api.CursorMeta;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.api.PageMeta;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.error.BusinessRuleException;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.RateLimitedException;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

record EnvelopeProbeItem(String name) {}

record EnvelopeProbeCreateRequest(
        @NotBlank @Size(min = 3, max = 120) String title,
        @Min(1) Integer openings) {}

@RestController
@RequestMapping("/probe")
public class EnvelopeWebProbeController {

    @GetMapping("/ok")
    EnvelopeProbeItem ok() {
        return new EnvelopeProbeItem("alpha");
    }

    @GetMapping("/page")
    PagedResponse<EnvelopeProbeItem> page() {
        return new PagedResponse<>(
                List.of(new EnvelopeProbeItem("alpha")),
                PageMeta.of(0, 20, 1));
    }

    @GetMapping("/cursor")
    CursorResponse<EnvelopeProbeItem> cursor() {
        return new CursorResponse<>(
                List.of(),
                new CursorMeta(null, false, 20));
    }

    @DeleteMapping("/gone")
    ResponseEntity<Void> gone() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/missing")
    EnvelopeProbeItem missing() {
        throw new ResourceNotFoundException("Job not found.");
    }

    @GetMapping("/conflict")
    EnvelopeProbeItem conflict() {
        throw new ConflictException(
                ErrorCode.STALE_VERSION,
                "The resource was modified by someone else.");
    }

    @GetMapping("/rule")
    EnvelopeProbeItem rule() {
        throw new BusinessRuleException("Profile is incomplete.");
    }

    @GetMapping("/limited")
    EnvelopeProbeItem limited() {
        throw new RateLimitedException("Too many requests.", 30);
    }

    @GetMapping("/boom")
    EnvelopeProbeItem boom() {
        throw new IllegalStateException(
                "internal detail jdbc:postgresql://secret-host/db");
    }

    @GetMapping("/typed")
    EnvelopeProbeItem typed(@RequestParam int n) {
        return new EnvelopeProbeItem(String.valueOf(n));
    }

    @PostMapping("/validate")
    EnvelopeProbeItem validate(@Valid @RequestBody EnvelopeProbeCreateRequest request) {
        return new EnvelopeProbeItem(request.title());
    }
}
