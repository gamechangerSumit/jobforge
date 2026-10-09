package com.jobforge.backend.search;

import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.SecurityRulesContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/**
 * Public read endpoints of the job catalog (API_CONTRACT §12.4, marked {@code P}).
 * {@code GET /jobs/{id}} is public for PUBLISHED jobs; the service hides other statuses from non-members (404),
 * and an optional bearer token still authenticates members. The skill autocomplete is public as well (§12.2).
 */
@Component
public class SearchSecurityRulesContributor implements SecurityRulesContributor {

    @Override
    public void contribute(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers(
                HttpMethod.GET,
                ApiPaths.BASE + "/jobs",
                ApiPaths.BASE + "/jobs/facets",
                ApiPaths.BASE + "/jobs/*",
                ApiPaths.BASE + "/jobs/*/similar",
                ApiPaths.BASE + "/skills").permitAll();
    }
}
