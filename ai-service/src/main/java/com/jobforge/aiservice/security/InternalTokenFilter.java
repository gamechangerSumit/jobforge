package com.jobforge.aiservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates backend-to-ai-service calls via X-Internal-Token using a constant-time comparison. */
public class InternalTokenFilter extends OncePerRequestFilter {
    private final byte[] expected;
    private final ObjectMapper mapper;

    public InternalTokenFilter(String expectedToken,ObjectMapper mapper) {
        if(expectedToken==null||expectedToken.isBlank())
            throw new IllegalStateException("jobforge.ai.internal-token (AI_INTERNAL_TOKEN) must be configured");
        this.expected=expectedToken.getBytes(StandardCharsets.UTF_8);
        this.mapper=mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String actual=request.getHeader("X-Internal-Token");
        if(actual==null||!MessageDigest.isEqual(expected,actual.getBytes(StandardCharsets.UTF_8))) {
            String requestId;
            try { requestId=UUID.fromString(request.getHeader("X-Request-Id")==null?"":request.getHeader("X-Request-Id")).toString(); }
            catch(IllegalArgumentException ex) { requestId=UUID.randomUUID().toString(); }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setHeader("X-Request-Id",requestId);
            Map<String,Object> error=new LinkedHashMap<>();
            error.put("code","AUTH_UNAUTHENTICATED");
            error.put("message","Invalid internal authentication.");
            error.put("status",401);
            error.put("requestId",requestId);
            error.put("timestamp",Instant.now().toString());
            error.put("path",request.getRequestURI());
            mapper.writeValue(response.getWriter(),Map.of("error",error));
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("backend",null,AuthorityUtils.createAuthorityList("ROLE_INTERNAL")));
        chain.doFilter(request,response);
    }
}
