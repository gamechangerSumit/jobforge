package com.jobforge.backend.shared.idempotency;

/**
 * Port for {@code Idempotency-Key} handling (API_CONTRACT §9: 24 h window). Production binding is Dev 3's Redis store
 * ({@code jf:{env}:idem:{userId}:{key}}); {@link InMemoryIdempotencyStore} remains only as a fallback for contexts without Redis.
 * Database constraints remain the source of truth for uniqueness.
 */
public interface IdempotencyStore {

    enum Kind { NEW, REPLAY, MISMATCH, IN_PROGRESS }

    record Outcome(Kind kind, Object response) {}

    /** @param scope {@code userId:key}; @param requestHash hash of method + path + canonical body */
    Outcome begin(String scope, String requestHash);

    void complete(String scope, Object response);

    /** Releases the key after a failed attempt so the client may retry. */
    void abort(String scope);
}
