package com.jobforge.backend.community.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.ValidationFailedException;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.jobforge.backend.community.domain.PostType;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates and normalises {@code typeMetadata} per post type (API_CONTRACT 12.9). Unknown keys, wrong types and
 * non-https URLs are 400 VALIDATION_FAILED with {@code typeMetadata.<key>} field paths. Output contains only the
 * allowed keys, so nothing unvalidated is persisted. Pure logic (no Spring) so it can be unit tested.
 */
@Component
public class TypeMetadataValidator {

    private static final Map<PostType, Set<String>> ALLOWED = Map.of(
            PostType.HIRING, Set.of("companyName", "roleTitle", "jobId", "location", "applyUrl"),
            PostType.REFERRAL, Set.of("companyName", "roleTitle", "referralDetails"),
            PostType.PROJECT, Set.of("repoUrl", "liveUrl", "techStack"),
            PostType.EVENT, Set.of("startsAt", "endsAt", "location", "onlineUrl", "registrationUrl"),
            PostType.TECHNICAL, Set.of("codeLanguage"),
            PostType.DISCUSSION, Set.of(),
            PostType.CAREER_ADVICE, Set.of());
    private static final Map<PostType, Set<String>> REQUIRED = Map.of(
            PostType.HIRING, Set.of("companyName", "roleTitle"),
            PostType.REFERRAL, Set.of("companyName", "roleTitle", "referralDetails"),
            PostType.EVENT, Set.of("startsAt"));
    private static final Set<String> URL_KEYS = Set.of("applyUrl", "repoUrl", "liveUrl", "onlineUrl", "registrationUrl");
    private static final int TEXT_MAX = 200;
    private static final int DETAILS_MAX = 2000;
    private static final int URL_MAX = 500;
    private static final int TECH_STACK_MAX = 20;

    private final ObjectMapper mapper;

    public TypeMetadataValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode validate(PostType type, JsonNode input) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        ObjectNode out = mapper.createObjectNode();
        JsonNode node = input == null || input.isNull() ? mapper.createObjectNode() : input;
        if (!node.isObject()) {
            throw ValidationFailedException.of("typeMetadata", "TYPE", "must be an object");
        }
        Set<String> allowed = ALLOWED.get(type);
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (!allowed.contains(key)) {
                errors.add(new FieldErrorDetail("typeMetadata." + key, "UNKNOWN_FIELD", "not allowed for " + type));
            }
        }
        for (String key : REQUIRED.getOrDefault(type, Set.of())) {
            JsonNode v = node.get(key);
            if (v == null || v.isNull() || (v.isTextual() && v.asText().isBlank())) {
                errors.add(new FieldErrorDetail("typeMetadata." + key, "REQUIRED", "is required for " + type));
            }
        }
        for (String key : allowed) {
            JsonNode v = node.get(key);
            if (v == null || v.isNull()) {
                continue;
            }
            switch (key) {
                case "techStack" -> techStack(v, out, errors);
                case "startsAt", "endsAt" -> instant(key, v, out, errors);
                case "jobId" -> jobId(v, out, errors);
                case "referralDetails" -> text(key, v, DETAILS_MAX, out, errors);
                default -> {
                    if (URL_KEYS.contains(key)) {
                        url(key, v, out, errors);
                    } else {
                        text(key, v, TEXT_MAX, out, errors);
                    }
                }
            }
        }
        if (type == PostType.EVENT && out.has("startsAt") && out.has("endsAt")
                && Instant.parse(out.get("endsAt").asText()).isBefore(Instant.parse(out.get("startsAt").asText()))) {
            errors.add(new FieldErrorDetail("typeMetadata.endsAt", "RANGE", "must not be before startsAt"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return out;
    }

    private static void text(String key, JsonNode v, int max, ObjectNode out, List<FieldErrorDetail> errors) {
        if (!v.isTextual()) {
            errors.add(new FieldErrorDetail("typeMetadata." + key, "TYPE", "must be a string"));
            return;
        }
        String value = v.asText().strip();
        if (value.isEmpty()) {
            return; // blank optional value = omitted
        }
        if (value.length() > max) {
            errors.add(new FieldErrorDetail("typeMetadata." + key, "SIZE", "must be at most " + max + " characters"));
            return;
        }
        out.put(key, com.jobforge.backend.shared.text.MarkdownSanitizer.strip(value));
    }

    private static void url(String key, JsonNode v, ObjectNode out, List<FieldErrorDetail> errors) {
        if (!v.isTextual()) {
            errors.add(new FieldErrorDetail("typeMetadata." + key, "TYPE", "must be a string"));
            return;
        }
        String value = v.asText().strip();
        if (value.isEmpty()) {
            return;
        }
        if (value.length() > URL_MAX || !isHttps(value)) {
            errors.add(new FieldErrorDetail("typeMetadata." + key, "URL", "must be an https URL up to " + URL_MAX + " characters"));
            return;
        }
        out.put(key, value);
    }

    static boolean isHttps(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void instant(String key, JsonNode v, ObjectNode out, List<FieldErrorDetail> errors) {
        if (!v.isTextual()) {
            errors.add(new FieldErrorDetail("typeMetadata." + key, "TYPE", "must be an ISO-8601 UTC timestamp"));
            return;
        }
        try {
            out.put(key, Instant.parse(v.asText().strip()).toString());
        } catch (DateTimeParseException e) {
            errors.add(new FieldErrorDetail("typeMetadata." + key, "FORMAT", "must be an ISO-8601 UTC timestamp"));
        }
    }

    private static void jobId(JsonNode v, ObjectNode out, List<FieldErrorDetail> errors) {
        try {
            out.put("jobId", UUID.fromString(v.asText()).toString());
        } catch (IllegalArgumentException e) {
            errors.add(new FieldErrorDetail("typeMetadata.jobId", "FORMAT", "must be a UUID"));
        }
    }

    private static void techStack(JsonNode v, ObjectNode out, List<FieldErrorDetail> errors) {
        if (!v.isArray() || v.size() > TECH_STACK_MAX) {
            errors.add(new FieldErrorDetail("typeMetadata.techStack", "SIZE", "must be an array of at most " + TECH_STACK_MAX + " strings"));
            return;
        }
        ArrayNode list = out.putArray("techStack");
        for (JsonNode item : v) {
            if (!item.isTextual() || item.asText().isBlank() || item.asText().strip().length() > 50) {
                errors.add(new FieldErrorDetail("typeMetadata.techStack", "ITEM", "each entry must be 1-50 characters"));
                return;
            }
            list.add(com.jobforge.backend.shared.text.MarkdownSanitizer.strip(item.asText().strip()));
        }
    }
}
