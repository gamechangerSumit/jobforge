package com.jobforge.backend.user.app;

import java.util.UUID;

/** Payloads of events published by the user module on {@code jobforge.users.v1} (no personal data). */
public final class UserEvents {

    private UserEvents() {}

    /** The account was anonymized (DELETE /users/me). Consumers purge personal interactions of {@code userId}. */
    public record UserDeleted(UUID userId) {}
}
