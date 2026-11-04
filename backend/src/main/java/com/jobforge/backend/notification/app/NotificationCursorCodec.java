package com.jobforge.backend.notification.app;

import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signed, opaque, 24-hour notification cursor bound to the notification list. */
@Component
public class NotificationCursorCodec {
    private static final long TTL_SECONDS = 86_400L;
    private final byte[] key;
    private final Clock clock;

    public NotificationCursorCodec(@Value("${jobforge.security.jwt.secret}") String jwtSecret, Clock clock) {
        this.key = sign(jwtSecret.getBytes(StandardCharsets.UTF_8), "jobforge-notification-cursor-v1".getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String encode(Instant createdAt, UUID id) {
        String payload = createdAt + "|" + id + "|" + (clock.instant().getEpochSecond() + TTL_SECONDS);
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(bytes) + "." + encoder.encodeToString(sign(key, bytes));
    }

    public Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String[] token = cursor.split("\\.", -1);
            if (token.length != 2) throw invalid();
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] payload = decoder.decode(token[0]);
            if (!MessageDigest.isEqual(decoder.decode(token[1]), sign(key, payload))) throw invalid();
            String[] fields = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 3 || Long.parseLong(fields[2]) < clock.instant().getEpochSecond()) throw invalid();
            return new Position(Instant.parse(fields[0]), UUID.fromString(fields[1]));
        } catch (RuntimeException ex) {
            if (ex instanceof ApiException api) throw api;
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.MALFORMED_REQUEST, "The notification cursor is invalid or has expired.");
    }

    private static byte[] sign(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    public record Position(Instant createdAt, UUID id) {}
}