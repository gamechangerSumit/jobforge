package com.jobforge.backend.ai;

import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.SecurityRulesContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

@Component
public class AiSecurityRulesContributor implements SecurityRulesContributor {
    @Override
    public void contribute(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/ai/**").authenticated();
        registry.requestMatchers(HttpMethod.POST, ApiPaths.BASE + "/ai/**").authenticated();
        registry.requestMatchers(HttpMethod.PUT, ApiPaths.BASE + "/ai/**").authenticated();
    }
}
