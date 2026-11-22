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

/** Minimizes persisted AI input and computes a deterministic SHA-256 idempotency fingerprint. */
@Component
public class InputSanitizer {
    private static final Pattern SECRET_KEY =
            Pattern.compile("(?i).*(password|passwd|secret|token|api[-_]?key|authorization|credential).*");
    private final ObjectMapper canonical;

    public InputSanitizer(ObjectMapper mapper) {
        this.canonical = mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }
    public JsonNode sanitize(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("AI input must be a JSON object");
        }
        ObjectNode copy = payload.deepCopy();
        copy.remove("context");
        strip(copy);
        return copy;
    }
    public String hash(JsonNode sanitized) {
        try {
            Object tree = canonical.treeToValue(sanitized, Object.class);
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
