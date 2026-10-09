package com.jobforge.backend.notification;

import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.SecurityRulesContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

@Component
public class NotificationSecurityRulesContributor
        implements SecurityRulesContributor {

    @Override
    public void contribute(
            AuthorizeHttpRequestsConfigurer<
                    HttpSecurity
            >.AuthorizationManagerRequestMatcherRegistry registry
    ) {

        registry.requestMatchers(
                HttpMethod.GET,
                ApiPaths.BASE + "/notifications/**"
        ).authenticated();

        registry.requestMatchers(
                HttpMethod.POST,
                ApiPaths.BASE + "/notifications/**"
        ).authenticated();
    }
}