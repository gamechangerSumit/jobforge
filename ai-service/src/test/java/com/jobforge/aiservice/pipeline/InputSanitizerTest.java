package com.jobforge.aiservice.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class InputSanitizerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final InputSanitizer sanitizer = new InputSanitizer(mapper);

    @Test
    void removesContextAndCredentialKeysRecursivelyWithoutMutatingInput() throws Exception {
        JsonNode input = mapper.readTree("""
                {"title":"Backend","context":{"phone":"123"},"apiKey":"secret",
                 "nested":{"Password":"hidden","safe":1,"items":[{"authToken":"x","value":2}]}}
                """);
        JsonNode output = sanitizer.sanitize(input);
        assertThat(output.has("context")).isFalse();
        assertThat(output.has("apiKey")).isFalse();
        assertThat(output.at("/nested/Password").isMissingNode()).isTrue();
        assertThat(output.at("/nested/safe").asInt()).isEqualTo(1);
        assertThat(output.at("/nested/items/0/authToken").isMissingNode()).isTrue();
        assertThat(output.at("/nested/items/0/value").asInt()).isEqualTo(2);
        assertThat(input.has("context")).isTrue();
    }

    @Test
    void hashIsStableAcrossObjectKeyOrderAndContextChanges() throws Exception {
        JsonNode first = sanitizer.sanitize(mapper.readTree(
                "{\"a\":1,\"b\":{\"x\":1,\"y\":2},\"context\":{\"user\":1}}"));
        JsonNode reordered = sanitizer.sanitize(mapper.readTree(
                "{\"b\":{\"y\":2,\"x\":1},\"a\":1}"));
        JsonNode changed = sanitizer.sanitize(mapper.readTree(
                "{\"a\":2,\"b\":{\"y\":2,\"x\":1}}"));
        assertThat(sanitizer.hash(first)).isEqualTo(sanitizer.hash(reordered)).hasSize(64);
        assertThat(sanitizer.hash(first)).isNotEqualTo(sanitizer.hash(changed));
    }

    @Test
    void rejectsNonObjectPayloads() throws Exception {
        assertThatThrownBy(() -> sanitizer.sanitize(mapper.readTree("[1,2,3]")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
