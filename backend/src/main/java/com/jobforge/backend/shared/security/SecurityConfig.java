package com.jobforge.backend.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.config.ApiPaths;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Layer 1 of authorization (ARCHITECTURE §9): coarse URL rules.
 * Layers 2/3 are @PreAuthorize and ownership policies in services.
 * Deny-by-default: anything not listed requires authentication.
 *
 * <p>CSRF protection is intentionally off for the API: it is bearer-token
 * only (no cookie auth); the one cookie ({@code jf_refresh}) is
 * SameSite=Strict, path-scoped and additionally requires
 * {@code X-Requested-With} (ARCHITECTURE §19).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String AUTH = ApiPaths.BASE + "/auth/";

    @Bean
    SecurityErrorHandlers securityErrorHandlers(
            ObjectMapper mapper,
            Clock clock) {
        return new SecurityErrorHandlers(mapper, clock);
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder decoder,
            AccessTokenVerifier verifier,
            SecurityErrorHandlers errors,
            @Qualifier("corsConfigurationSource")
            CorsConfigurationSource cors,
            ObjectProvider<SecurityRulesContributor> contributors)
            throws Exception {

        http.csrf(csrf -> csrf.disable())
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> {
                    // Error dispatches (unmapped route, 404/405, exceptions) must reach the error handling layer
                    // instead of being re-secured, which used to turn every unmapped URL into a misleading 403.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();

                    auth.requestMatchers(
                                    HttpMethod.GET,
                                    "/actuator/health",
                                    "/actuator/health/**")
                            .permitAll();

                    auth.requestMatchers(
                                    HttpMethod.POST,
                                    AUTH + "register",
                                    AUTH + "login",
                                    AUTH + "refresh",
                                    AUTH + "verify-email",
                                    AUTH + "resend-verification",
                                    AUTH + "forgot-password",
                                    AUTH + "reset-password")
                            .permitAll();

                    auth.requestMatchers(
                                    ApiPaths.BASE + "/admin/**")
                            .hasRole("ADMIN");

                    contributors.orderedStream()
                            .forEach(c -> c.contribute(auth));

                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j
                                .decoder(decoder)
                                .jwtAuthenticationConverter(
                                        new JwtPrincipalConverter(verifier)))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors));

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            SecurityProperties properties) {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(
                properties.corsAllowedOrigins());

        config.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"));

        config.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "X-Request-Id",
                        "X-Requested-With",
                        "If-Match",
                        "Idempotency-Key"));

        config.setExposedHeaders(
                List.of(
                        "X-Request-Id",
                        "ETag",
                        "Retry-After",
                        "Location"));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                ApiPaths.BASE + "/**",
                config);

        return source;
    }
}