package com.jobforge.backend.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Prefixes every application {@code @RestController} with {@code /api/v1}. Controllers declare
 * only their resource path (e.g. {@code @RequestMapping("/jobs")}). Actuator is not affected.
 */
@Configuration
public class ApiPathConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                ApiPaths.BASE,
                HandlerTypePredicate.forBasePackage("com.jobforge.backend")
                        .and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
