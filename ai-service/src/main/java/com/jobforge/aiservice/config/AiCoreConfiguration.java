package com.jobforge.aiservice.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared UTC clock and JDBC transaction boundary for the AI service. */
@Configuration
@EnableConfigurationProperties(AiRuntimeProperties.class)
public class AiCoreConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }
    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
}
