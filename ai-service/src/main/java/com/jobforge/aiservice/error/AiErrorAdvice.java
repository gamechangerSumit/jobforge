package com.jobforge.aiservice.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
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
        HttpHeaders headers=new HttpHeaders();
        if(ex.retryAfterSeconds()!=null) headers.set(HttpHeaders.RETRY_AFTER,Long.toString(ex.retryAfterSeconds()));
        return ResponseEntity.status(status).headers(headers).body(envelope(ex.code().name(),ex.getMessage(),
                status,request,ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,Object>> handleValidation(MethodArgumentNotValidException ex,HttpServletRequest request) {
        List<AiServiceException.Detail> details=ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new AiServiceException.Detail(error.getField(),
                        error.getCode()==null?"VALIDATION_FAILED":error.getCode().toUpperCase(),
                        "The supplied value is invalid."))
                .toList();
        return ResponseEntity.badRequest().body(envelope("VALIDATION_FAILED",
                "One or more fields are invalid.",HttpStatus.BAD_REQUEST,request,details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String,Object>> handleMalformed(HttpMessageNotReadableException ex,HttpServletRequest request) {
        return ResponseEntity.badRequest().body(envelope("MALFORMED_REQUEST","The request body is invalid.",
                HttpStatus.BAD_REQUEST,request,List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,Object>> handleIllegalArgument(IllegalArgumentException ex,HttpServletRequest request) {
        return ResponseEntity.badRequest().body(envelope("VALIDATION_FAILED","One or more fields are invalid.",
                HttpStatus.BAD_REQUEST,request,List.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,Object>> handleUnexpected(Exception ex,HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(envelope("INTERNAL_ERROR",
                "An unexpected error occurred. Use the requestId when contacting support.",
                HttpStatus.INTERNAL_SERVER_ERROR,request,List.of()));
    }

    private Map<String,Object> envelope(String code,String message,HttpStatus status,HttpServletRequest request,
            List<AiServiceException.Detail> details) {
        String requestId=request.getHeader("X-Request-Id");
        try { requestId=UUID.fromString(requestId==null?"":requestId).toString(); }
        catch (IllegalArgumentException ex) { requestId=UUID.randomUUID().toString(); }
        Map<String,Object> error=new java.util.LinkedHashMap<>();
        error.put("code",code); error.put("message",message); error.put("status",status.value());
        error.put("requestId",requestId); error.put("timestamp",Instant.now().toString());
        error.put("path",request.getRequestURI());
        if(details!=null&&!details.isEmpty()) error.put("details",details);
        return Map.of("error",error);
    }
}
