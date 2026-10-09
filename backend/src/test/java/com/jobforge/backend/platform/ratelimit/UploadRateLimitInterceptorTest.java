package com.jobforge.backend.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class UploadRateLimitInterceptorTest {

    private final RateLimiter limiter = mock(RateLimiter.class);
    private final UploadRateLimitInterceptor interceptor = new UploadRateLimitInterceptor(limiter);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesTheClientIpWhenAnonymous() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/users/me/avatar");
        request.setRemoteAddr("203.0.113.9");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(limiter).check(eq(RateLimitClass.UPLOAD), eq("ip:203.0.113.9"));
    }

    @Test
    void ignoresReadRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/seekers/me/resumes");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(limiter, never()).check(any(), any());
    }
}
