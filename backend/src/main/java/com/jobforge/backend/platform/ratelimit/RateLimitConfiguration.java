package com.jobforge.backend.platform.ratelimit;

import com.jobforge.backend.shared.config.ApiPaths;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the UPLOAD rate class on the three binary-upload endpoints (no-op when the limiter bean is disabled). */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<RateLimiter> limiter;

    public RateLimitConfiguration(ObjectProvider<RateLimiter> limiter) {
        this.limiter = limiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimiter instance = limiter.getIfAvailable();
        if (instance == null) {
            return;
        }
        String[] uploadPaths = {
                ApiPaths.BASE + "/users/me/avatar",
                ApiPaths.BASE + "/companies/*/logo",
                ApiPaths.BASE + "/seekers/me/resumes"};
        registry.addInterceptor(new UploadRateLimitInterceptor(instance)).addPathPatterns(uploadPaths);
        registry.addInterceptor(new RequestRateLimitInterceptor(instance)).addPathPatterns(ApiPaths.BASE + "/**")
                .excludePathPatterns(uploadPaths);
    }
}
