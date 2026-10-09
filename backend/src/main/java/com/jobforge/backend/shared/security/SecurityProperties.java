package com.jobforge.backend.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code jobforge.security.*}. The JWT secret has no default and is never printed. */
@ConfigurationProperties(prefix = "jobforge.security")
public record SecurityProperties(
        Jwt jwt,
        @DefaultValue("http://localhost:3000") List<String> corsAllowedOrigins,
        @DefaultValue("12") int bcryptStrength) {

    public record Jwt(String secret, @DefaultValue("15") int accessTtlMinutes) {
        @Override
        public String toString() {
            return "Jwt[secret=***, accessTtlMinutes=" + accessTtlMinutes + "]";
        }
    }
}
