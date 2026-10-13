package com.jobforge.backend.community.app;

import com.jobforge.backend.community.app.CommunityRecords.CursorPos;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Opaque, signed, 24 h cursors (API_CONTRACT 6). The HMAC key is derived from the JWT secret with a fixed label, so no
 * new secret/env var is introduced. Format: base64url(payload) "." base64url(hmac). A cursor is bound to its list
 * kind, so a feed cursor cannot be replayed on the comments list.
 */
@Component
public class CursorCodec {

    private static final long TTL_SECONDS = 24 * 3600L;

    private final byte[] key;
    private final Clock clock;

    public CursorCodec(@Value("${jobforge.security.jwt.secret}") String jwtSecret, Clock clock) {
        this.key = hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), "community-cursor-v1".getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String encode(String kind, CursorPos pos) {
        long expires = clock.instant().getEpochSecond() + TTL_SECONDS;
        String payload = kind + "|" + pos.ts() + "|" + (pos.score() == null ? "" : pos.score()) + "|" + pos.id() + "|" + expires;
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        return b64.encodeToString(body) + "." + b64.encodeToString(hmac(key, body));
    }

    /** @return null for a missing/blank cursor (first page). */
    public CursorPos decode(String kind, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2) {
                throw invalid();
            }
            byte[] body = Base64.getUrlDecoder().decode(parts[0]);
            byte[] mac = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(mac, hmac(key, body))) { // constant-time compare
                throw invalid();
            }
            String[] f = new String(body, StandardCharsets.UTF_8).split("\\|", -1);
            if (f.length != 5 || !f[0].equals(kind) || Long.parseLong(f[4]) < clock.instant().getEpochSecond()) {
                throw invalid();
            }
            return new CursorPos(Instant.parse(f[1]), f[2].isEmpty() ? null : Long.valueOf(f[2]), UUID.fromString(f[3]));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.MALFORMED_REQUEST, "The cursor is invalid or has expired.");
    }

    private static byte[] hmac(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
