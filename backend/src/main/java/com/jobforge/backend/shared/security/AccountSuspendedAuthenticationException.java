package com.jobforge.backend.shared.security;

import org.springframework.security.core.AuthenticationException;

/** Valid token, suspended account → 403 ACCOUNT_SUSPENDED. */
public class AccountSuspendedAuthenticationException extends AuthenticationException {

    public AccountSuspendedAuthenticationException() {
        super("Account suspended");
    }
}
