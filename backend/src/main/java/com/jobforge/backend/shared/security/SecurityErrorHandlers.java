package com.jobforge.backend.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobforge.backend.shared.api.ErrorBody;
import com.jobforge.backend.shared.api.ErrorEnvelope;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Writes the contract error envelope for 401/403 produced by the security filter chain. */
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;
    private final Clock clock;

    public SecurityErrorHandlers(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        ErrorCode code = ErrorCode.AUTH_UNAUTHENTICATED;
        if (ex instanceof AccountSuspendedAuthenticationException) {
            code = ErrorCode.ACCOUNT_SUSPENDED;
        } else if (isExpired(ex)) {
            code = ErrorCode.AUTH_TOKEN_EXPIRED;
        }
        if (code.status().value() == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        String message = switch (code) {
            case ACCOUNT_SUSPENDED -> "This account is suspended.";
            case AUTH_TOKEN_EXPIRED -> "The access token has expired.";
            default -> "Authentication is required.";
        };
        write(request, response, code, message);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, ErrorCode.ACCESS_DENIED, "You do not have access to this resource.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        ErrorBody body = new ErrorBody(code.name(), message, code.status().value(), RequestContext.requestId(request),
                clock.instant().truncatedTo(ChronoUnit.SECONDS), request.getRequestURI(), null);
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), new ErrorEnvelope(body));
    }

    private static boolean isExpired(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("expired")) {
                return true;
            }
        }
        return false;
    }
}
