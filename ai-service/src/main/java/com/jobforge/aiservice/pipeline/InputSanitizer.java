package com.jobforge.aiservice.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Removes credentials, separates ephemeral backend context from persisted input, and hashes user payloads. */
@Component
public class InputSanitizer {
    private static final Pattern SECRET_KEY =
            Pattern.compile("(?i).*(password|passwd|secret|token|api[-_]?key|authorization|credential).*");
    private final ObjectMapper canonical;

    public InputSanitizer(ObjectMapper mapper) {
        this.canonical = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /** Sanitized payload sent to provider; trusted backend context remains available for generation. */
    public JsonNode sanitize(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("AI input must be a JSON object");
        }
        ObjectNode copy = payload.deepCopy();
        strip(copy);
        return copy;
    }

    /** Persist only caller request data. Backend context can contain profile/company PII and is never stored. */
    public JsonNode forPersistence(JsonNode sanitized) {
        ObjectNode copy = sanitized.deepCopy();
        copy.remove("context");
        return copy;
    }

    public String hash(JsonNode sanitized) {
        try {
            JsonNode safe = forPersistence(sanitized);
            Object tree = canonical.treeToValue(safe, Object.class);
            byte[] bytes = canonical.writeValueAsString(tree).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.io.IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash AI input", e);
        }
    }

    private static void strip(JsonNode node) {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String, JsonNode>> it = object.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                if (SECRET_KEY.matcher(entry.getKey()).matches()) it.remove();
                else strip(entry.getValue());
            }
        } else if (node instanceof ArrayNode array) {
            array.forEach(InputSanitizer::strip);
        }
    }
}
