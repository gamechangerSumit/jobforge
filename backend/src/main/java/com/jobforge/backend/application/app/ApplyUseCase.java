package com.jobforge.backend.application.app;

import com.jobforge.backend.application.app.ApplicationCommands.Apply;
import com.jobforge.backend.application.app.ApplicationViews.ApplicationView;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.idempotency.IdempotencyStore;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Apply with {@code Idempotency-Key} (API_CONTRACT §9). Not transactional itself: the application transaction commits
 * inside {@link ApplicationService#submit} before the result is stored for replay. The DB unique constraint stays the
 * source of truth (D-17), so a store outage can never create a second application.
 */
@Service
public class ApplyUseCase {

    public record Result(ApplicationView view, boolean replayed) {}

    private final ApplicationService applications;
    private final IdempotencyStore store;

    public ApplyUseCase(ApplicationService applications, IdempotencyStore store) {
        this.applications = applications;
        this.store = store;
    }

    public Result apply(AuthenticatedUser seeker, UUID jobId, Apply command, String idempotencyKey) {
        String scope = seeker.id() + ":apply:" + idempotencyKey;
        String hash = sha256(jobId + "|" + command.resumeId() + "|" + (command.coverLetter() == null ? "" : command.coverLetter()));
        IdempotencyStore.Outcome outcome = store.begin(scope, hash);
        switch (outcome.kind()) {
            case REPLAY:
                return new Result((ApplicationView) outcome.response(), true);
            case MISMATCH:
                throw new ConflictException(ErrorCode.CONFLICT, "This Idempotency-Key was already used with a different request.");
            case IN_PROGRESS:
                throw new ConflictException(ErrorCode.CONFLICT, "A request with this Idempotency-Key is still in progress.");
            default:
                break;
        }
        try {
            ApplicationView view = applications.submit(seeker, jobId, command);
            store.complete(scope, view);
            return new Result(view, false);
        } catch (RuntimeException e) {
            store.abort(scope);
            throw e;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
