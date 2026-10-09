package com.jobforge.backend.user.app;

import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.user.facade.NewUserCommand;
import com.jobforge.backend.user.facade.UserAccountView;
import com.jobforge.backend.user.facade.UserFacade;
import com.jobforge.backend.storage.facade.ImageUploads;
import com.jobforge.backend.storage.facade.ObjectStorage;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.config.ApiPaths;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService implements UserFacade {

    private final UserRepository users;
    private final ObjectStorage storage;
    private final AuditService audit;
    private final Clock clock;

    public UserAccountService(UserRepository users, ObjectStorage storage, AuditService audit, Clock clock) {
        this.users = users;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccountView> findById(UUID id) {
        return users.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccountView> findByEmail(String email) {
        return users.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean handleTaken(String handle) {
        return users.handleExists(handle);
    }

    @Override
    @Transactional
    public UserAccountView create(NewUserCommand command) {
        UUID id = UUID.randomUUID();
        users.insert(id, command, clock.instant());
        return users.findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public void recordLogin(UUID userId, Instant at) {
        users.recordLogin(userId, at);
    }

    @Override
    @Transactional
    public void markEmailVerified(UUID userId, Instant at) {
        users.markEmailVerified(userId, at);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, String newPasswordHash, boolean bumpTokenVersion) {
        users.updatePassword(userId, newPasswordHash, bumpTokenVersion, clock.instant());
    }

    // ---- /users/me ----

    @Transactional(readOnly = true)
    public UserAccountView getMe(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    /** JSON-Merge-Patch semantics for non-nullable fields: null/absent = unchanged. */
    @Transactional
    public UserAccountView updateMe(UUID userId, String firstName, String lastName, String handle) {
        UserAccountView current = getMe(userId);
        String newFirst = firstName != null ? firstName.trim() : current.firstName();
        String newLast = lastName != null ? lastName.trim() : current.lastName();
        String newHandle = handle != null ? handle : current.handle();
        boolean changed = !newFirst.equals(current.firstName())
                || !newLast.equals(current.lastName())
                || !newHandle.equalsIgnoreCase(current.handle());
        if (changed && !users.updateNames(userId, newFirst, newLast, newHandle, current.version(), clock.instant())) {
            throw new ConflictException(ErrorCode.STALE_VERSION, "The account was modified concurrently. Retry.");
        }
        return getMe(userId);
    }

    @Override
    @Transactional
    public void anonymize(UUID userId, String unusablePasswordHash) {
        UserAccountView user = getMe(userId);
        String suffix = userId.toString().replace("-", "").substring(0, 12);
        users.findAvatarKey(userId).ifPresent(this::deleteQuietly);
        users.anonymize(userId, "deleted-" + suffix + "@deleted.invalid", "deleted_" + suffix, unusablePasswordHash,
                clock.instant());
        audit.record(AuditEntry.success(AuditAction.USER_DELETED, "User", userId, userId, user.role()));
    }

    // ---- avatar ----

    public record AvatarContent(byte[] bytes, String contentType, String key) {}

    /** @return the stored object key (cache-busting: a new key per upload). */
    @Transactional
    public String uploadAvatar(UUID userId, byte[] content) {
        UserAccountView user = getMe(userId);
        ImageUploads.Type type = ImageUploads.verify(content);
        String previous = users.findAvatarKey(userId).orElse(null);
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + "." + type.extension();
        storage.store(key, content);
        users.setAvatarKey(userId, key, clock.instant());
        if (previous != null) {
            deleteQuietly(previous);
        }
        audit.record(AuditEntry.success(AuditAction.USER_AVATAR_CHANGED, "User", userId, userId, user.role()));
        return key;
    }

    @Transactional(readOnly = true)
    public AvatarContent loadAvatar(UUID userId) {
        String key = users.findAvatarKey(userId).orElseThrow(() -> new ResourceNotFoundException("Avatar not found."));
        return new AvatarContent(storage.load(key), ImageUploads.Type.fromExtension(key).contentType(), key);
    }

    /** Relative URL served by {@code GET /users/{id}/avatar}; null when no avatar is set. */
    @Transactional(readOnly = true)
    public String avatarUrl(UUID userId) {
        return users.findAvatarKey(userId).map(k -> ApiPaths.BASE + "/users/" + userId + "/avatar?v=" + Integer.toHexString(k.hashCode()))
                .orElse(null);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ignored) {
            // orphaned object is harmless; the DB no longer references it
        }
    }
}
