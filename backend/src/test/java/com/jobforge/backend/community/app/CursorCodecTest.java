package com.jobforge.backend.community.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobforge.backend.community.app.CommunityRecords.CursorPos;
import com.jobforge.backend.shared.error.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** NOT RUN in the authoring session. */
class CursorCodecTest {

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

    private CursorCodec codec(String secret, Instant at) {
        return new CursorCodec(secret, Clock.fixed(at, ZoneOffset.UTC));
    }

    @Test
    void roundTripsPositionIncludingScore() {
        CursorPos pos = new CursorPos(Instant.parse("2026-10-09T08:15:30.123456Z"), 42L, UUID.randomUUID());
        CursorCodec codec = codec("0123456789abcdef0123456789abcdef", NOW);
        assertThat(codec.decode("feed:TRENDING", codec.encode("feed:TRENDING", pos))).isEqualTo(pos);
    }

    @Test
    void blankCursorMeansFirstPage() {
        assertThat(codec("secret-secret-secret-secret-1234", NOW).decode("saved", " ")).isNull();
    }

    @Test
    void rejectsTamperedWrongKindWrongKeyAndExpiredCursors() {
        CursorCodec codec = codec("0123456789abcdef0123456789abcdef", NOW);
        String token = codec.encode("saved", new CursorPos(NOW, null, UUID.randomUUID()));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThatThrownBy(() -> codec.decode("saved", tampered)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec.decode("feed:LATEST", token)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec("another-secret-another-secret-12", NOW).decode("saved", token)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec("0123456789abcdef0123456789abcdef", NOW.plus(Duration.ofHours(25))).decode("saved", token))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec.decode("saved", "garbage")).isInstanceOf(ApiException.class);
    }
}
