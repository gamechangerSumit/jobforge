package com.jobforge.backend.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/** Access to per-request correlation data set by {@link RequestIdFilter}. */
public final class RequestContext {

    private RequestContext() {}

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return value instanceof String s ? s : UUID.randomUUID().toString();
    }
}
