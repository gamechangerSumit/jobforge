package com.jobforge.backend.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Caller network metadata. {@code getRemoteAddr()} is the client IP: with {@code server.forward-headers-strategy=native}
 * Tomcat substitutes X-Forwarded-For only when the direct peer is an internal proxy (never for untrusted peers). User agent is truncated to the column size (255).
 */
public record ClientInfo(String ip, String userAgent, String requestId) {

    public static ClientInfo from(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if (ip != null) {
            int zone = ip.indexOf('%'); // IPv6 scope ids are not valid inet text
            ip = zone >= 0 ? ip.substring(0, zone) : ip;
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 255) {
            ua = ua.substring(0, 255);
        }
        return new ClientInfo(ip, ua, RequestContext.requestId(request));
    }

    /** Current request, or empty info outside a request (schedulers, tests). */
    public static ClientInfo current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return from(attributes.getRequest());
        }
        return new ClientInfo(null, null, null);
    }
}
