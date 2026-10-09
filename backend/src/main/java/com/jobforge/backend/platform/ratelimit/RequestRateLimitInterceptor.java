package com.jobforge.backend.platform.ratelimit;

import com.jobforge.backend.shared.config.ApiPaths;
import com.jobforge.backend.shared.security.AuthenticatedUser;
import com.jobforge.backend.shared.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the AUTH, SEARCH and DEFAULT classes (API_CONTRACT section 9) to every /api/v1 request that is not an upload
 * (uploads have their own interceptor). Per authenticated user, per IP when anonymous; AUTH is always per IP.
 * {@code /auth/me} and {@code /auth/refresh} count as DEFAULT: they are called on every page load / silent refresh and
 * are not credential-guessing surfaces.
 */
public class RequestRateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;

    public RequestRateLimitInterceptor(RateLimiter limiter) {
        this.limiter = limiter;
    }

    static RateLimitClass classify(String method, String uri) {
        String path = uri.startsWith(ApiPaths.BASE) ? uri.substring(ApiPaths.BASE.length()) : uri;
        if (path.startsWith("/auth/") && !path.equals("/auth/me") && !path.equals("/auth/refresh")) {
            return RateLimitClass.AUTH;
        }
        if (HttpMethod.GET.matches(method) && (path.equals("/jobs") || path.equals("/jobs/facets")
                || path.matches("/jobs/[^/]+/similar") || path.equals("/skills") || path.equals("/users/search"))) {
            return RateLimitClass.SEARCH;
        }
        return RateLimitClass.DEFAULT;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        RateLimitClass rateClass = classify(request.getMethod(), request.getRequestURI());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String subject = rateClass != RateLimitClass.AUTH && auth != null && auth.getPrincipal() instanceof AuthenticatedUser user
                ? "u:" + user.id()
                : "ip:" + ClientInfo.from(request).ip();
        limiter.check(rateClass, subject);
        return true;
    }
}
