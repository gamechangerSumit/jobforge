package com.jobforge.backend.company;

import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.SecurityRulesContributor;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

/** Company catalog is public (API_CONTRACT §12.3); {@code /companies/me} is registered first and stays recruiter-only. */
@Component
public class CompanySecurityRulesContributor implements SecurityRulesContributor {

    @Override
    public void contribute(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        registry.requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/companies/me").hasRole("RECRUITER");
        registry.requestMatchers(HttpMethod.GET, ApiPaths.BASE + "/companies", ApiPaths.BASE + "/companies/*",
                ApiPaths.BASE + "/companies/*/logo").permitAll();
    }
}
