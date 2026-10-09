package com.jobforge.backend.shared.web;

import com.jobforge.backend.shared.api.ApiResponse;
import com.jobforge.backend.shared.api.CursorResponse;
import com.jobforge.backend.shared.api.ErrorEnvelope;
import com.jobforge.backend.shared.api.PagedResponse;
import com.jobforge.backend.shared.api.ResponseMeta;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps controller return values into the success envelope (API_CONTRACT §4, ARCHITECTURE §7).
 * Scoped to application packages so Actuator endpoints are never wrapped.
 * Controllers return plain DTOs, {@link PagedResponse} or {@link CursorResponse}.
 */
@RestControllerAdvice(basePackages = "com.jobforge.backend")
public class ResponseEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    private final Clock clock;

    public ResponseEnvelopeAdvice(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body == null
                || body instanceof ApiResponse<?>
                || body instanceof ErrorEnvelope
                || body instanceof ProblemDetail) {
            return body;
        }
        String requestId = request instanceof ServletServerHttpRequest servletRequest
                ? RequestContext.requestId(servletRequest.getServletRequest())
                : null;
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        if (body instanceof PagedResponse<?> paged) {
            return new ApiResponse<>(paged.items(), new ResponseMeta(requestId, now, paged.page(), null));
        }
        if (body instanceof CursorResponse<?> cursor) {
            return new ApiResponse<>(cursor.items(), new ResponseMeta(requestId, now, null, cursor.cursor()));
        }
        return new ApiResponse<>(body, ResponseMeta.of(requestId, now));
    }
}
