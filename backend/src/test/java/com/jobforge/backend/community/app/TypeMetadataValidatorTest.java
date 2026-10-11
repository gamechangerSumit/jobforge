package com.jobforge.backend.community.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.community.domain.PostType;
import com.jobforge.backend.shared.error.ValidationFailedException;
import org.junit.jupiter.api.Test;

/** NOT RUN in the authoring session. */
class TypeMetadataValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TypeMetadataValidator validator = new TypeMetadataValidator(mapper);

    private com.fasterxml.jackson.databind.JsonNode json(String raw) throws Exception {
        return mapper.readTree(raw);
    }

    @Test
    void discussionAcceptsNoMetadataButRejectsUnknownKeys() throws Exception {
        assertThat(validator.validate(PostType.DISCUSSION, null).size()).isZero();
        assertThatThrownBy(() -> validator.validate(PostType.DISCUSSION, json("{\"x\":1}")))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void hiringRequiresCompanyAndRoleAndHttpsApplyUrl() throws Exception {
        assertThatThrownBy(() -> validator.validate(PostType.HIRING, json("{\"companyName\":\"Acme\"}")))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> validator.validate(PostType.HIRING,
                json("{\"companyName\":\"Acme\",\"roleTitle\":\"Dev\",\"applyUrl\":\"http://insecure.example\"}")))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> validator.validate(PostType.HIRING,
                json("{\"companyName\":\"Acme\",\"roleTitle\":\"Dev\",\"applyUrl\":\"javascript:alert(1)\"}")))
                .isInstanceOf(ValidationFailedException.class);
        var ok = validator.validate(PostType.HIRING,
                json("{\"companyName\":\"Acme\",\"roleTitle\":\"Dev\",\"applyUrl\":\"https://acme.example/apply\"}"));
        assertThat(ok.get("applyUrl").asText()).isEqualTo("https://acme.example/apply");
    }

    @Test
    void eventRequiresValidInstantAndOrderedRange() throws Exception {
        assertThatThrownBy(() -> validator.validate(PostType.EVENT, json("{\"startsAt\":\"tomorrow\"}")))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> validator.validate(PostType.EVENT,
                json("{\"startsAt\":\"2026-11-02T10:00:00Z\",\"endsAt\":\"2026-11-01T10:00:00Z\"}")))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(validator.validate(PostType.EVENT, json("{\"startsAt\":\"2026-11-01T10:00:00Z\"}")).has("startsAt")).isTrue();
    }

    @Test
    void projectTechStackIsBoundedAndHtmlIsStripped() throws Exception {
        var out = validator.validate(PostType.PROJECT, json("{\"techStack\":[\"<b>java</b>\",\"sql\"]}"));
        assertThat(out.get("techStack").get(0).asText()).isEqualTo("java");
        StringBuilder many = new StringBuilder("{\"techStack\":[");
        for (int i = 0; i < 21; i++) many.append(i == 0 ? "" : ",").append("\"t").append(i).append("\"");
        many.append("]}");
        assertThatThrownBy(() -> validator.validate(PostType.PROJECT, json(many.toString())))
                .isInstanceOf(ValidationFailedException.class);
    }
}
