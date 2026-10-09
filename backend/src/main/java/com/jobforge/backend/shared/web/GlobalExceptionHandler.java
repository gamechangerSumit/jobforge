package com.jobforge.backend.shared.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.jobforge.backend.shared.api.ErrorBody;
import com.jobforge.backend.shared.api.ErrorEnvelope;
import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;
import com.jobforge.backend.shared.error.FieldErrorDetail;
import com.jobforge.backend.shared.error.RateLimitedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;

/**
 * The single exception → error-envelope mapper (API_CONTRACT §5, ARCHITECTURE §20).
 * Only codes from the contract catalog are used. Stack traces, SQL and class names are never exposed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApi(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        if (code.status().is5xxServerError()) {
            log.error("API error code={} requestId={}", code, RequestContext.requestId(request), ex);
        }
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitedException limited) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(limited.retryAfterSeconds()));
        }
        return respond(code, ex.getMessage(), request, ex.details(), headers);
    }

    /** Method-security (@PreAuthorize) denials raised inside controllers; URL-rule denials are handled by the security chain. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.ACCESS_DENIED, "You do not have access to this resource.", request, List.of(), null);
    }

    @ExceptionHandler(BindException.class) // also covers MethodArgumentNotValidException
    public ResponseEntity<ErrorEnvelope> handleBind(BindException ex, HttpServletRequest request) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(new FieldErrorDetail(fe.getField(), constraintCode(fe.getCode()), fe.getDefaultMessage()));
        }
        for (ObjectError ge : ex.getBindingResult().getGlobalErrors()) {
            details.add(new FieldErrorDetail(ge.getObjectName(), constraintCode(ge.getCode()), ge.getDefaultMessage()));
        }
        return validationFailed(details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors errors) {
                for (FieldError fe : errors.getFieldErrors()) {
                    details.add(new FieldErrorDetail(fe.getField(), constraintCode(fe.getCode()), fe.getDefaultMessage()));
                }
            } else {
                String name = result.getMethodParameter().getParameterName();
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    String[] codes = error.getCodes();
                    String last = codes == null || codes.length == 0 ? null : codes[codes.length - 1];
                    details.add(new FieldErrorDetail(name == null ? "parameter" : name, constraintCode(last), error.getDefaultMessage()));
                }
            }
        }
        return validationFailed(details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String field = null;
            for (Path.Node node : violation.getPropertyPath()) {
                field = node.getName();
            }
            String annotation = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            details.add(new FieldErrorDetail(field, constraintCode(annotation), violation.getMessage()));
        }
        return validationFailed(details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return validationFailed(
                    List.of(new FieldErrorDetail(jsonPath(unknown), "UNKNOWN_FIELD", "unknown field")), request);
        }
        if (cause instanceof InvalidFormatException format
                && format.getTargetType() != null
                && format.getTargetType().isEnum()) {
            return validationFailed(
                    List.of(new FieldErrorDetail(jsonPath(format), "INVALID_ENUM", "must be one of the allowed values")),
                    request);
        }
        return respond(ErrorCode.MALFORMED_REQUEST, "The request body is missing or malformed.", request, List.of(), null);
    }

    @ExceptionHandler({
        TypeMismatchException.class, // includes MethodArgumentTypeMismatchException
        ServletRequestBindingException.class, // includes missing request parameter
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorEnvelope> handleBadParameter(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, "A request parameter is missing or has an invalid value.", request, List.of(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleUnsupportedMediaType(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "The request content type is not supported.", request, List.of(), null);
    }

    // The contract has no 405/406 codes. Interim mapping — see docs/requests/REQ-20261001-error-catalog-gaps.md
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorEnvelope> handleNotAcceptable(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.MALFORMED_REQUEST, "The requested response format is not supported.", request, List.of(), null);
    }

    @ExceptionHandler({
        HttpRequestMethodNotSupportedException.class,
        NoHandlerFoundException.class,
        NoResourceFoundException.class
    })
    public ResponseEntity<ErrorEnvelope> handleNotFound(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, "The requested resource was not found.", request, List.of(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleTooLarge(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.PAYLOAD_TOO_LARGE, "The request payload is too large.", request, List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = RequestContext.requestId(request);
        log.error("Unhandled exception requestId={}", requestId, ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request, List.of(), null);
    }

    // ---- helpers ----

    private ResponseEntity<ErrorEnvelope> validationFailed(List<FieldErrorDetail> details, HttpServletRequest request) {
        return respond(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", request, details, null);
    }

    private ResponseEntity<ErrorEnvelope> respond(
            ErrorCode code, String message, HttpServletRequest request, List<FieldErrorDetail> details, HttpHeaders extra) {
        ErrorBody body = new ErrorBody(
                code.name(),
                message,
                code.status().value(),
                RequestContext.requestId(request),
                clock.instant().truncatedTo(ChronoUnit.SECONDS),
                request.getRequestURI(),
                details);
        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(code.status()).contentType(MediaType.APPLICATION_JSON);
        if (extra != null) {
            builder.headers(extra);
        }
        return builder.body(new ErrorEnvelope(body));
    }

    /** "NotBlank" → "NOT_BLANK", "Size" → "SIZE" (API_CONTRACT §5.1 detail codes). */
    static String constraintCode(String code) {
        if (code == null || code.isBlank()) {
            return "INVALID";
        }
        return code.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
    }

    private static String jsonPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
