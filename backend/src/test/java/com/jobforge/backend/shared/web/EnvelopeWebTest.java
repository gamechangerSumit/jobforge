
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

import com.jobforge.backend.shared.config.ClockConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the success/error envelope against API_CONTRACT §4/§5
 * through a test-only probe controller.
 */
@WebMvcTest(controllers = EnvelopeWebProbeController.class)
@Import({
        ClockConfig.class,
        EnvelopeWebTest.OpenSecurity.class
})
@ActiveProfiles("web-probe")
class EnvelopeWebTest {
private static final String BASE = "/api/v1/probe";

    @Autowired
    private MockMvc mvc;

    // ---- Success envelope ----

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
        mvc.perform(delete(BASE + "/gone"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    // ---- Error envelope ----

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

    // ---- Validation ----

    @Test
    void beanValidationFailuresListFieldAndConstraintCode() throws Exception {
        mvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"ab\",\"openings\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath(
                        "$.error.details[?(@.field=='title')].code",
                        hasItem("SIZE")))
                .andExpect(jsonPath(
                        "$.error.details[?(@.field=='openings')].code",
                        hasItem("MIN")));
    }

    @Test
    void missingRequiredFieldReportsNotBlank() throws Exception {
        mvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openings\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(
                        "$.error.details[?(@.field=='title')].code",
                        hasItem("NOT_BLANK")));
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
        mvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
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
        mvc.perform(post(BASE + "/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ---- Unknown routes ----

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

    // ---- Test-only configuration ----

    /**
     * Security is covered by integration tests.
     * This MVC slice focuses on response envelopes.
     */
    @TestConfiguration
    static class OpenSecurity {

        @Bean
        SecurityFilterChain openChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

}
