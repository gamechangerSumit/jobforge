package com.jobforge.backend.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestRateLimitClassifierTest {

    @Test
    void credentialEndpointsAreAuthClass() {
        assertThat(RequestRateLimitInterceptor.classify("POST", "/api/v1/auth/login")).isEqualTo(RateLimitClass.AUTH);
        assertThat(RequestRateLimitInterceptor.classify("POST", "/api/v1/auth/register")).isEqualTo(RateLimitClass.AUTH);
        assertThat(RequestRateLimitInterceptor.classify("POST", "/api/v1/auth/forgot-password")).isEqualTo(RateLimitClass.AUTH);
    }

    @Test
    void sessionBootstrapEndpointsAreNotAuthClass() {
        assertThat(RequestRateLimitInterceptor.classify("POST", "/api/v1/auth/refresh")).isEqualTo(RateLimitClass.DEFAULT);
        assertThat(RequestRateLimitInterceptor.classify("GET", "/api/v1/auth/me")).isEqualTo(RateLimitClass.DEFAULT);
    }

    @Test
    void publicDiscoveryReadsAreSearchClass() {
        assertThat(RequestRateLimitInterceptor.classify("GET", "/api/v1/jobs")).isEqualTo(RateLimitClass.SEARCH);
        assertThat(RequestRateLimitInterceptor.classify("GET", "/api/v1/jobs/facets")).isEqualTo(RateLimitClass.SEARCH);
        assertThat(RequestRateLimitInterceptor.classify("GET", "/api/v1/jobs/3f2a/similar")).isEqualTo(RateLimitClass.SEARCH);
        assertThat(RequestRateLimitInterceptor.classify("GET", "/api/v1/skills")).isEqualTo(RateLimitClass.SEARCH);
    }

    @Test
    void everythingElseIsDefault() {
        assertThat(RequestRateLimitInterceptor.classify("GET", "/api/v1/jobs/3f2a")).isEqualTo(RateLimitClass.DEFAULT);
        assertThat(RequestRateLimitInterceptor.classify("POST", "/api/v1/jobs")).isEqualTo(RateLimitClass.DEFAULT);
        assertThat(RequestRateLimitInterceptor.classify("GET", "/api/v1/companies/me")).isEqualTo(RateLimitClass.DEFAULT);
    }
}
