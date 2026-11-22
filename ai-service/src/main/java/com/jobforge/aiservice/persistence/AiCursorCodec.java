package com.jobforge.aiservice.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Signed, opaque, 24-hour cursor for deterministic AI request history pagination. */
@Component
public class AiCursorCodec {
    public record Cursor(Instant createdAt, UUID id, Instant expiresAt) {}
    private final byte[] secret;
    private final ObjectMapper mapper;

    public AiCursorCodec(@Value("${jobforge.ai.internal-token}") String secret,ObjectMapper mapper) {
        this.secret=secret.getBytes(StandardCharsets.UTF_8);
        this.mapper=mapper;
    }

    public String encode(Instant createdAt,UUID id) {
        try {
            Cursor cursor=new Cursor(createdAt,id,Instant.now().plusSeconds(86400));
            String payload=Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mapper.writeValueAsBytes(cursor));
            return payload+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
        } catch(Exception ex) {
            throw new IllegalStateException("Could not encode AI cursor",ex);
        }
    }

    public Cursor decode(String token) {
        try {
            String[] parts=token.split("\\.",-1);
            if(parts.length!=2) throw new IllegalArgumentException();
            byte[] supplied=Base64.getUrlDecoder().decode(parts[1]);
            if(!MessageDigest.isEqual(sign(parts[0]),supplied)) throw new IllegalArgumentException();
            Cursor cursor=mapper.readValue(Base64.getUrlDecoder().decode(parts[0]),Cursor.class);
            if(cursor.expiresAt()==null||cursor.expiresAt().isBefore(Instant.now())
                    ||cursor.createdAt()==null||cursor.id()==null) throw new IllegalArgumentException();
            return cursor;
        } catch(Exception ex) {
            throw new IllegalArgumentException("Invalid or expired cursor");
        }
    }

    private byte[] sign(String payload) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret,"HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    }
}
