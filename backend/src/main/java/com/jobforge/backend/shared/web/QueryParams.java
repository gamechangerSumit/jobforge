package com.jobforge.backend.shared.web;

import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.ValidationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Per-endpoint query whitelist + typed accessors (API_CONTRACT §6–8). Unknown parameter → 400 VALIDATION_FAILED. */
public final class QueryParams {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final HttpServletRequest request;

    public QueryParams(HttpServletRequest request, String... allowed) {
        this.request = request;
        Set<String> whitelist = Arrays.stream(allowed).collect(Collectors.toSet());
        List<FieldErrorDetail> unknown = new ArrayList<>();
        for (String name : request.getParameterMap().keySet()) {
            if (!whitelist.contains(name)) {
                unknown.add(new FieldErrorDetail(name, "UNKNOWN_FIELD", "unknown query parameter"));
            }
        }
        if (!unknown.isEmpty()) {
            throw new ValidationFailedException(unknown);
        }
    }

    public String string(String name) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID uuid(String name) {
        String value = string(name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Parameter '" + name + "' must be a UUID.");
        }
    }

    public <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        String value = string(name);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw ValidationFailedException.of(name, "INVALID_ENUM", "must be one of the allowed values");
        }
    }

    public int page() {
        int page = intValue("page", 0);
        if (page < 0) {
            throw ValidationFailedException.of("page", "MIN", "must be greater than or equal to 0");
        }
        return page;
    }

    public int size() {
        int size = intValue("size", DEFAULT_SIZE);
        if (size < 1) {
            throw ValidationFailedException.of("size", "MIN", "must be greater than or equal to 1");
        }
        if (size > MAX_SIZE) {
            throw ValidationFailedException.of("size", "MAX", "must be less than or equal to " + MAX_SIZE);
        }
        return size;
    }

    public List<String> sortValues() {
        String[] values = request.getParameterValues("sort");
        return values == null ? List.of() : Arrays.asList(values);
    }

    private int intValue(String name, int fallback) {
        String value = string(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.MALFORMED_REQUEST, "Parameter '" + name + "' must be an integer.");
        }
    }
}
