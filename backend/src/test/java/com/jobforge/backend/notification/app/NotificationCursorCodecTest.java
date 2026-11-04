package com.jobforge.backend.notification.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobforge.backend.shared.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationCursorCodecTest {
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Test
    void signedCursorRoundTripsAndIsBoundToSecretAndExpiry() {
        NotificationCursorCodec codec = new NotificationCursorCodec("0123456789abcdef0123456789abcdef",
                Clock.fixed(NOW, ZoneOffset.UTC));
        Instant createdAt = NOW.minusSeconds(120);
        UUID id = UUID.randomUUID();
        String cursor = codec.encode(createdAt, id);
        assertThat(codec.decode(cursor)).isEqualTo(new NotificationCursorCodec.Position(createdAt, id));
        NotificationCursorCodec wrongKey = new NotificationCursorCodec("another-secret-another-secret-1234",
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> wrongKey.decode(cursor)).isInstanceOf(ApiException.class);
        NotificationCursorCodec expired = new NotificationCursorCodec("0123456789abcdef0123456789abcdef",
                Clock.fixed(NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC));
        assertThatThrownBy(() -> expired.decode(cursor)).isInstanceOf(ApiException.class);
    }

    @Test
    void blankCursorMeansFirstPageAndTamperingIsRejected() {
        NotificationCursorCodec codec = new NotificationCursorCodec("0123456789abcdef0123456789abcdef",
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(codec.decode(null)).isNull();
        String cursor = codec.encode(NOW, UUID.randomUUID());
        String tampered = cursor.substring(0, cursor.length() - 1) + (cursor.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> codec.decode(tampered)).isInstanceOf(ApiException.class);
    }
}
