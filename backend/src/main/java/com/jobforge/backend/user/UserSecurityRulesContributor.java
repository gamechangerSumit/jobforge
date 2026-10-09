package com.jobforge.backend.user;

import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.SecurityRulesContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/** Avatar images are public so that img tags work without the in-memory bearer token. Only GET of this exact shape. */
@Component
public class UserSecurityRulesContributor implements SecurityRulesContributor {

    @Override
    public void contribute(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/users/*/avatar").permitAll();
    }
}
