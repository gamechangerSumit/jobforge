package com.jobforge.backend.shared.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Hook for other modules (e.g. Dev 3's ai/notification) to register URL rules without editing
 * {@code SecurityConfig} (ARCHITECTURE §25.3). Rules are evaluated before the final {@code authenticated()} fallback.
 */
public interface SecurityRulesContributor {

    void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry);
}
