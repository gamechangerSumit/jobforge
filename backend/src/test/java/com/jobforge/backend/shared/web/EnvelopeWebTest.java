package com.jobforge.backend.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobforge.backend.shared.api.CursorMeta;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.api.PageMeta;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.config.ApiPathConfig;
import com.jobforge.backend.shared.config.ClockConfig;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Verifies the success/error envelope against API_CONTRACT §4/§5 through a throw-away probe controller. */
@WebMvcTest(controllers = EnvelopeWebTest.ProbeController.class)
@Import({ClockConfig.class, ApiPathConfig.class})
@ActiveProfiles("web-probe")
class EnvelopeWebTest {

    private static final String BASE = "/api/v1/probe";

    @Autowired
    private MockMvc mvc;

    // ---- success envelope ----

    @Test
    void singleResourceIsWrappedWithRequestIdAndUtcTimestamp() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get(BASE + "/ok").header("X-Request-Id", id))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", id))
                .andExpect(jsonPath("$.data.name").value("alpha"))
                .andExpect(jsonPath("$.meta.requestId").value(id))
                .andExpect(jsonPath("$.meta.timestamp", endsWith("Z")))
                .andExpect(jsonPath("$.meta.page").doesNotExist())
                .andExpect(jsonPath("$.meta.cursor").doesNotExist());
    }

    @Test
    void offsetPageProducesPageMeta() throws Exception {
        mvc.perform(get(BASE + "/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("alpha"))
                .andExpect(jsonPath("$.meta.page.number").value(0))
                .andExpect(jsonPath("$.meta.page.size").value(20))
                .andExpect(jsonPath("$.meta.page.totalElements").value(1))
                .andExpect(jsonPath("$.meta.page.totalPages").value(1))
                .andExpect(jsonPath("$.meta.page.hasNext").value(false));
    }

    @Test
    void cursorPageProducesCursorMetaWithExplicitNullNext() throws Exception {
        mvc.perform(get(BASE + "/cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.cursor.next").value((Object) null))
                .andExpect(jsonPath("$.meta.cursor.hasNext").value(false))
                .andExpect(jsonPath("$.meta.cursor.limit").value(20));
    }

    @Test
    void noContentHasNoBody() throws Exception {
        mvc.perform(delete(BASE + "/gone")).andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    // ---- error envelope ----

    @Test
    void notFoundUsesErrorEnvelope() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get(BASE + "/missing").header("X-Request-Id", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.status").value(404))
                .andExpect(jsonPath("$.error.requestId").value(id))
                .andExpect(jsonPath("$.error.path").value(BASE + "/missing"))
                .andExpect(jsonPath("$.error.timestamp", endsWith("Z")))
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void conflictFamilyKeepsSpecificCode() throws Exception {
        mvc.perform(get(BASE + "/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STALE_VERSION"));
    }

    @Test
    void businessRuleIs422() throws Exception {
        mvc.perform(get(BASE + "/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATED"));
    }

    @Test
    void rateLimitedSetsRetryAfter() throws Exception {
        mvc.perform(get(BASE + "/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void unexpectedExceptionNeverLeaksInternals() throws Exception {
        mvc.perform(get(BASE + "/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void beanValidationFailuresListFieldAndConstraintCode() throws Exception {
        mvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"ab\",\"openings\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[?(@.field=='title')].code", hasItem("SIZE")))
                .andExpect(jsonPath("$.error.details[?(@.field=='openings')].code", hasItem("MIN")));
    }

    @Test
    void missingRequiredFieldReportsNotBlank() throws Exception {
        mvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON).content("{\"openings\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[?(@.field=='title')].code", hasItem("NOT_BLANK")));
    }

    @Test
    void unknownRequestFieldsAreRejected() throws Exception {
        mvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"valid title\",\"bogus\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("bogus"))
                .andExpect(jsonPath("$.error.details[0].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void unparseableJsonIsMalformedRequest() throws Exception {
        mvc.perform(post(BASE + "/validate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void badParameterTypeIsMalformedRequest() throws Exception {
        mvc.perform(get(BASE + "/typed").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post(BASE + "/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownRouteIsResourceNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void wrongMethodIsMappedToNotFoundUntilContractDefines405() throws Exception {
        mvc.perform(post(BASE + "/ok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---- probe controller (test-only; lives in an api-less package, excluded from ArchUnit) ----

    /** Security is covered by the *IT tests; this slice only verifies the envelope. */
    @TestConfiguration
    static class OpenSecurity {
        @Bean
        SecurityFilterChain openChain(HttpSecurity http) throws Exception {
            return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
        }
    }

    record Item(String name) {}

    record CreateRequest(@NotBlank @Size(min = 3, max = 120) String title, @Min(1) Integer openings) {}

    @Profile("web-probe") // keeps the probe out of full-context ITs
    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @GetMapping("/ok")
        Item ok() {
            return new Item("alpha");
        }

        @GetMapping("/page")
        PagedResponse<Item> page() {
            return new PagedResponse<>(List.of(new Item("alpha")), PageMeta.of(0, 20, 1));
        }

        @GetMapping("/cursor")
        CursorResponse<Item> cursor() {
            return new CursorResponse<>(List.of(), new CursorMeta(null, false, 20));
        }

        @DeleteMapping("/gone")
        ResponseEntity<Void> gone() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/missing")
        Item missing() {
            throw new ResourceNotFoundException("Job not found.");
        }

        @GetMapping("/conflict")
        Item conflict() {
            throw new ConflictException(ErrorCode.STALE_VERSION, "The resource was modified by someone else.");
        }

        @GetMapping("/rule")
        Item rule() {
            throw new BusinessRuleException("Profile is incomplete.");
        }

        @GetMapping("/limited")
        Item limited() {
            throw new RateLimitedException("Too many requests.", 30);
        }

        @GetMapping("/boom")
        Item boom() {
            throw new IllegalStateException("internal detail jdbc:postgresql://secret-host/db");
        }

        @GetMapping("/typed")
        Item typed(@RequestParam int n) {
            return new Item(String.valueOf(n));
        }

        @PostMapping("/validate")
        Item validate(@Valid @RequestBody CreateRequest request) {
            return new Item(request.title());
        }
    }
}
