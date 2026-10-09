package com.jobforge.backend.shared.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Interim single-instance implementation (TTL 24 h). Not for multi-instance production. */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);

    private record Entry(String hash, Object response, boolean done, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Outcome begin(String scope, String requestHash) {
        Instant now = clock.instant();
        if (entries.size() > 10_000) {
            entries.values().removeIf(e -> e.expiresAt().isBefore(now));
        }
        Outcome[] result = new Outcome[1];
        entries.compute(scope, (k, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                result[0] = new Outcome(Kind.NEW, null);
                return new Entry(requestHash, null, false, now.plus(TTL));
            }
            if (!existing.hash().equals(requestHash)) {
                result[0] = new Outcome(Kind.MISMATCH, null);
            } else if (existing.done()) {
                result[0] = new Outcome(Kind.REPLAY, existing.response());
            } else {
                result[0] = new Outcome(Kind.IN_PROGRESS, null);
            }
            return existing;
        });
        return result[0];
    }

    @Override
    public void complete(String scope, Object response) {
        entries.computeIfPresent(scope, (k, e) -> new Entry(e.hash(), response, true, e.expiresAt()));
    }

    @Override
    public void abort(String scope) {
        entries.computeIfPresent(scope, (k, e) -> e.done() ? e : null);
    }
}
