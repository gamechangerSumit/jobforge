package com.jobforge.backend.auth.app;

import com.jobforge.backend.auth.app.AuthResults.Me;
import com.jobforge.backend.auth.app.AuthResults.Session;
import com.jobforge.backend.auth.app.RefreshTokenStore.StoredRefreshToken;
import com.jobforge.backend.auth.app.VerificationTokenStore.StoredVerificationToken;
import com.jobforge.backend.auth.app.VerificationTokenStore.Type;
import com.jobforge.backend.company.facade.CompanyAccessFacade;
import com.jobforge.backend.profile.facade.ProfileFacade;
import com.jobforge.backend.shared.audit.AuditAction;
import com.jobforge.backend.shared.audit.AuditEntry;
import com.jobforge.backend.shared.audit.AuditOutcome;
import com.jobforge.backend.shared.audit.AuditService;
import com.jobforge.backend.shared.domain.UserRole;
import com.jobforge.backend.shared.domain.UserStatus;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ConflictException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.ResourceNotFoundException;
import com.jobforge.backend.shared.web.ClientInfo;
import com.jobforge.backend.user.facade.NewUserCommand;
import com.jobforge.backend.user.facade.UserAccountView;
import com.jobforge.backend.user.facade.UserFacade;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Authentication use cases (API_CONTRACT §3, §12.1; ARCHITECTURE §8; D-05).
 * Methods that must persist audit/revocation side-effects even when they end in an error use
 * {@code noRollbackFor = ApiException.class}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserFacade users;
    private final ProfileFacade profiles;
    private final CompanyAccessFacade companies;
    private final RefreshTokenStore refreshTokens;
    private final VerificationTokenStore verificationTokens;
    private final PasswordEncoder encoder;
    private final AccessTokenIssuer accessTokens;
    private final AuthMailer mailer;
    private final AuditService audit;
    private final AuthProperties props;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserFacade users, ProfileFacade profiles, CompanyAccessFacade companies, RefreshTokenStore refreshTokens,
            VerificationTokenStore verificationTokens, PasswordEncoder encoder, AccessTokenIssuer accessTokens,
            AuthMailer mailer, AuditService audit, AuthProperties props, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.companies = companies;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.encoder = encoder;
        this.accessTokens = accessTokens;
        this.mailer = mailer;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString()); // equalizes timing for unknown emails
    }

    // ------------------------------------------------------------ register

    public record RegisterCommand(String email, String password, String firstName, String lastName, UserRole role, String handle) {
        @Override
        public String toString() {
            return "RegisterCommand[role=" + role + "]";
        }
    }

    @Transactional
    public UserAccountView register(RegisterCommand c) {
        String email = c.email().trim().toLowerCase(Locale.ROOT);
        PasswordPolicy.check("password", c.password(), email);
        String handle = c.handle() != null ? c.handle() : generateHandle(c.firstName());
        UserAccountView user = users.create(new NewUserCommand(email, encoder.encode(c.password()), c.role(),
                c.firstName().trim(), c.lastName().trim(), handle));
        profiles.createInitialProfile(user.id(), user.role());
        audit.record(AuditEntry.success(AuditAction.USER_REGISTERED, "User", user.id(), user.id(), user.role()));
        issueVerificationToken(user);
        return user;
    }

    /** Interim: the contract does not say how a missing handle is derived (see REQ-20261002). */
    private String generateHandle(String firstName) {
        String base = firstName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.length() < 3) {
            base = "user";
        }
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        for (int i = 0; i < 10; i++) {
            String candidate = base + "_" + String.format("%06x", RANDOM.nextInt(0x1000000));
            if (!users.handleTaken(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException(ErrorCode.HANDLE_TAKEN, "Could not allocate a handle. Please provide one.");
    }

    // ------------------------------------------------------------ login / refresh / logout

    @Transactional(noRollbackFor = ApiException.class)
    public Session login(String rawEmail, String password, ClientInfo client) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<UserAccountView> found = users.findByEmail(email);
        boolean matches = encoder.matches(password, found.map(UserAccountView::passwordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches || found.get().status() == UserStatus.DELETED) {
            audit.record(AuditEntry.failure(AuditAction.USER_LOGIN_FAILED, "User", found.map(UserAccountView::id).orElse(null)));
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid email or password.");
        }
        UserAccountView user = found.get();
        if (user.status() == UserStatus.SUSPENDED) {
            audit.record(AuditEntry.failure(AuditAction.USER_LOGIN_FAILED, "User", user.id()));
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "This account is suspended.");
        }
        Instant now = clock.instant();
        users.recordLogin(user.id(), now);
        String refresh = startRefreshFamily(user.id(), UUID.randomUUID(), now, client);
        audit.record(AuditEntry.success(AuditAction.USER_LOGIN_SUCCESS, "User", user.id(), user.id(), user.role()));
        return session(user, refresh);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String refreshToken, ClientInfo client) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh token is missing or invalid.");
        }
        Instant now = clock.instant();
        StoredRefreshToken stored = refreshTokens.findByHashForUpdate(TokenCodec.sha256Hex(refreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh token is missing or invalid."));
        if (stored.replacedById() != null) { // already rotated → reuse (ARCHITECTURE §8.5)
            refreshTokens.revokeFamily(stored.familyId(), now);
            audit.record(AuditEntry.success(AuditAction.TOKEN_REUSE_DETECTED, "User", stored.userId(), null, null));
            throw new ApiException(ErrorCode.AUTH_REFRESH_REUSED, "Refresh token reuse detected. Please sign in again.");
        }
        if (stored.revokedAt() != null || !stored.expiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh token is missing or invalid.");
        }
        UserAccountView user = users.findById(stored.userId())
                .filter(u -> u.status() != UserStatus.DELETED)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh token is missing or invalid."));
        if (user.status() == UserStatus.SUSPENDED) {
            refreshTokens.revokeFamily(stored.familyId(), now);
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "This account is suspended.");
        }
        UUID newId = UUID.randomUUID();
        String newToken = TokenCodec.newToken();
        refreshTokens.insert(newId, user.id(), stored.familyId(), TokenCodec.sha256Hex(newToken),
                now.plus(Duration.ofDays(props.refreshTtlDays())), now, client.ip(), client.userAgent());
        refreshTokens.markRotated(stored.id(), newId, now);
        return session(user, newToken);
    }

    @Transactional
    public void logout(UUID userId, UserRole role, String refreshToken) {
        Instant now = clock.instant();
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.findByHashForUpdate(TokenCodec.sha256Hex(refreshToken))
                    .filter(t -> t.userId().equals(userId))
                    .ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), now));
        }
        audit.record(AuditEntry.success(AuditAction.USER_LOGOUT, "User", userId, userId, role));
    }

    // ------------------------------------------------------------ email verification

    @Transactional
    public void verifyEmail(String token) {
        Instant now = clock.instant();
        StoredVerificationToken stored = findValid(token, Type.EMAIL_VERIFICATION, now);
        verificationTokens.markUsed(stored.id(), now);
        users.markEmailVerified(stored.userId(), now);
        UserAccountView user = users.findById(stored.userId()).orElseThrow();
        audit.record(AuditEntry.success(AuditAction.EMAIL_VERIFIED, "User", user.id(), user.id(), user.role()));
    }

    @Transactional
    public void resendVerification(String rawEmail) {
        users.findByEmail(rawEmail.trim().toLowerCase(Locale.ROOT))
                .filter(u -> u.status() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueVerificationToken);
    }

    // ------------------------------------------------------------ password reset / change

    @Transactional
    public void forgotPassword(String rawEmail) {
        users.findByEmail(rawEmail.trim().toLowerCase(Locale.ROOT))
                .filter(u -> u.status() == UserStatus.ACTIVE || u.status() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(user -> {
                    Instant now = clock.instant();
                    verificationTokens.deleteUnused(user.id(), Type.PASSWORD_RESET);
                    String token = TokenCodec.newToken();
                    verificationTokens.insert(UUID.randomUUID(), user.id(), Type.PASSWORD_RESET, TokenCodec.sha256Hex(token),
                            now.plus(Duration.ofMinutes(props.passwordResetTtlMinutes())), now);
                    afterCommit(() -> send(() -> mailer.sendPasswordReset(user.email(), user.firstName(), token)));
                });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        StoredVerificationToken stored = findValid(token, Type.PASSWORD_RESET, now);
        UserAccountView user = users.findById(stored.userId()).orElseThrow();
        PasswordPolicy.check("newPassword", newPassword, user.email()); // before consuming: a weak password must not burn the token
        verificationTokens.markUsed(stored.id(), now);
        users.changePassword(user.id(), encoder.encode(newPassword), true); // bump → all access tokens die
        refreshTokens.revokeAllForUser(user.id(), now);
        audit.record(AuditEntry.success(AuditAction.PASSWORD_RESET, "User", user.id(), user.id(), user.role()));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(UUID userId, String currentPassword, String newPassword, String currentRefreshToken) {
        UserAccountView user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED, "Authentication is required."));
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            audit.record(new AuditEntry(AuditAction.PASSWORD_CHANGED, "User", userId, userId, user.role(),
                    AuditOutcome.FAILURE, null, null, null));
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "The current password is incorrect.");
        }
        PasswordPolicy.check("newPassword", newPassword, user.email());
        users.changePassword(userId, encoder.encode(newPassword), false);
        Instant now = clock.instant();
        Optional<StoredRefreshToken> current = currentRefreshToken == null || currentRefreshToken.isBlank()
                ? Optional.empty()
                : refreshTokens.findByHashForUpdate(TokenCodec.sha256Hex(currentRefreshToken)).filter(t -> t.userId().equals(userId));
        if (current.isPresent()) {
            refreshTokens.revokeAllForUserExceptFamily(userId, current.get().familyId(), now);
        } else {
            refreshTokens.revokeAllForUser(userId, now);
        }
        audit.record(AuditEntry.success(AuditAction.PASSWORD_CHANGED, "User", userId, userId, user.role()));
    }

    // ------------------------------------------------------------ admin provisioning (API_CONTRACT 12.14)

    /** Creates an ADMIN with an unusable random password and mails a set-password link (valid for the verification TTL). */
    @Transactional
    public UserAccountView createAdmin(UUID actorId, String rawEmail, String firstName, String lastName) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists.");
        }
        UserAccountView user = users.create(new NewUserCommand(email, encoder.encode(TokenCodec.newToken()), UserRole.ADMIN,
                firstName.trim(), lastName.trim(), generateHandle(firstName)));
        Instant now = clock.instant();
        users.markEmailVerified(user.id(), now); // the invite link proves mailbox ownership; status becomes ACTIVE
        String token = TokenCodec.newToken();
        verificationTokens.insert(UUID.randomUUID(), user.id(), Type.PASSWORD_RESET, TokenCodec.sha256Hex(token),
                now.plus(Duration.ofHours(props.verificationTtlHours())), now);
        audit.record(AuditEntry.success(AuditAction.ADMIN_CREATED, "User", user.id(), actorId, UserRole.ADMIN));
        afterCommit(() -> send(() -> mailer.sendPasswordReset(user.email(), user.firstName(), token)));
        return users.findById(user.id()).orElseThrow();
    }

    // ------------------------------------------------------------ account deletion (API_CONTRACT 12.2)

    /** Password-confirmed deletion: anonymizes the account, scrubs the profile, revokes every session. */
    @Transactional(noRollbackFor = ApiException.class)
    public void deleteAccount(UUID userId, String password) {
        UserAccountView user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_UNAUTHENTICATED, "Authentication is required."));
        if (!encoder.matches(password, user.passwordHash())) {
            audit.record(new AuditEntry(AuditAction.USER_DELETED, "User", userId, userId, user.role(),
                    AuditOutcome.FAILURE, null, null, null));
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "The password is incorrect.");
        }
        if (user.role() == UserRole.ADMIN) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATED, "Administrator accounts cannot be deleted here.");
        }
        // Policy (REQ-20261009): owners must first remove other members and close open jobs.
        companies.accountDeletionBlocker(userId).ifPresent(reason -> {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATED, reason);
        });
        Instant now = clock.instant();
        companies.releaseMembership(userId);
        profiles.scrubOnAccountDeletion(userId);
        refreshTokens.revokeAllForUser(userId, now);
        users.anonymize(userId, encoder.encode(TokenCodec.newToken())); // records USER_DELETED; bumps token_version
    }

    // ------------------------------------------------------------ me

    @Transactional(readOnly = true)
    public Me me(UUID userId) {
        UserAccountView user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        boolean recruiterApproved = user.role() == UserRole.RECRUITER && profiles.isRecruiterApproved(userId);
        return new Me(user, recruiterApproved, false); // companyVerified: companies arrive in the next slice
    }

    // ------------------------------------------------------------ internals

    private Session session(UserAccountView user, String refreshToken) {
        return new Session(accessTokens.issue(user), accessTokens.expiresInSeconds(), refreshToken,
                Duration.ofDays(props.refreshTtlDays()).toSeconds(), user);
    }

    private String startRefreshFamily(UUID userId, UUID familyId, Instant now, ClientInfo client) {
        String token = TokenCodec.newToken();
        refreshTokens.insert(UUID.randomUUID(), userId, familyId, TokenCodec.sha256Hex(token),
                now.plus(Duration.ofDays(props.refreshTtlDays())), now, client.ip(), client.userAgent());
        return token;
    }

    private void issueVerificationToken(UserAccountView user) {
        Instant now = clock.instant();
        verificationTokens.deleteUnused(user.id(), Type.EMAIL_VERIFICATION);
        String token = TokenCodec.newToken();
        verificationTokens.insert(UUID.randomUUID(), user.id(), Type.EMAIL_VERIFICATION, TokenCodec.sha256Hex(token),
                now.plus(Duration.ofHours(props.verificationTtlHours())), now);
        afterCommit(() -> send(() -> mailer.sendVerification(user.email(), user.firstName(), token)));
    }

    /** Invalid, used or expired tokens → 422 (contract does not define a code; see REQ-20261002). */
    private StoredVerificationToken findValid(String token, Type type, Instant now) {
        return verificationTokens.findByHashForUpdate(TokenCodec.sha256Hex(token), type)
                .filter(t -> t.usedAt() == null && t.expiresAt().isAfter(now))
                .orElseThrow(() -> new ApiException(ErrorCode.BUSINESS_RULE_VIOLATED, "The token is invalid or has expired."));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Mail problems must not fail the request and must not leak the token into logs. */
    private void send(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Security email could not be sent: {}", e.getClass().getSimpleName());
        }
    }
}
