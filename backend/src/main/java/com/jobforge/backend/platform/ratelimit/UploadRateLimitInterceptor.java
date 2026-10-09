package com.jobforge.backend.platform.ratelimit;

import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the UPLOAD class to avatar, company-logo and resume uploads. Runs after Spring Security, so the caller is
 * known; the direct peer IP is the fallback subject. Throws {@code RateLimitedException}, which the
 * {@code GlobalExceptionHandler} turns into 429 + Retry-After.
 */
public class UploadRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;

    public UploadRateLimitInterceptor(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        if (!HttpMethod.PUT.matches(method) && !HttpMethod.POST.matches(method)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String subject = auth != null && auth.getPrincipal() instanceof AuthenticatedUser user
                ? "u:" + user.id()
                : "ip:" + ClientInfo.from(request).ip();
        limiter.check(RateLimitClass.UPLOAD, subject);
        return true;
    }
}
