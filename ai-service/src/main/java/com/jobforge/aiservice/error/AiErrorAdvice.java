package com.jobforge.aiservice.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Contract-compatible error envelope. Never returns exception messages or stack traces for unknown failures. */
@RestControllerAdvice
public class AiErrorAdvice {
    @ExceptionHandler(AiServiceException.class)
    ResponseEntity<Map<String,Object>> handleAi(AiServiceException ex,HttpServletRequest request) {
        HttpStatus status=ex.code().status();
        String id=requestId(request);
        var builder=ResponseEntity.status(status).header("X-Request-Id",id);
        if(ex.retryAfterSeconds()!=null) builder.header(HttpHeaders.RETRY_AFTER,Long.toString(ex.retryAfterSeconds()));
        if(ex.replayed()) builder.header("Idempotency-Replayed","true");
        return builder.body(envelope(ex.code().name(),ex.getMessage(),status,request,ex.details(),id));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,Object>> handleValidation(MethodArgumentNotValidException ex,HttpServletRequest request) {
        List<AiServiceException.Detail> details=ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new AiServiceException.Detail(error.getField(),
                        error.getCode()==null?"VALIDATION_FAILED":error.getCode().toUpperCase(),
                        "The supplied value is invalid."))
                .toList();
        return response(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","One or more fields are invalid.",request,details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String,Object>> handleMalformed(HttpMessageNotReadableException ex,HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,"MALFORMED_REQUEST","The request body is invalid.",request,List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,Object>> handleIllegalArgument(IllegalArgumentException ex,HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","One or more fields are invalid.",request,List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> handleUnexpected(Exception ex,HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR",
                "An unexpected error occurred. Use the requestId when contacting support.",request,List.of());
    }

    private ResponseEntity<Map<String,Object>> response(HttpStatus status,String code,String message,
            HttpServletRequest request,List<AiServiceException.Detail> details) {
        String id=requestId(request);
        return ResponseEntity.status(status).header("X-Request-Id",id)
                .body(envelope(code,message,status,request,details,id));
    }

    private String requestId(HttpServletRequest request) {
        try { return UUID.fromString(request.getHeader("X-Request-Id")==null?"":request.getHeader("X-Request-Id")).toString(); }
        catch(IllegalArgumentException ex) { return UUID.randomUUID().toString(); }
    }

    private Map<String,Object> envelope(String code,String message,HttpStatus status,HttpServletRequest request,
            List<AiServiceException.Detail> details,String requestId) {
        Map<String,Object> error=new LinkedHashMap<>();
        error.put("code",code); error.put("message",message); error.put("status",status.value());
        error.put("requestId",requestId); error.put("timestamp",Instant.now().toString());
        error.put("path",request.getRequestURI());
        if(details!=null&&!details.isEmpty()) error.put("details",details);
        return Map.of("error",error);
    }
}
