package com.jobforge.backend.auth.app;

/**
 * Delivers security emails. Interim SMTP implementation (Mailpit in dev) until Dev 3's outbox/email_outbox
 * pipeline exists — see docs/requests/REQ-20261002-phase1-contract-gaps.md. Implementations must not log tokens.
 */
public interface AuthMailer {

    void sendVerification(String toEmail, String firstName, String token);

    void sendPasswordReset(String toEmail, String firstName, String token);
}
