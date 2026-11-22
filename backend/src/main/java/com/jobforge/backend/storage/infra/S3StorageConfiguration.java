package com.jobforge.backend.storage.infra;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** S3 client configuration; endpoint override enables MinIO without changing the storage port. */
@Configuration
@ConditionalOnProperty(name = "jobforge.storage.provider", havingValue = "s3")
public class S3StorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client s3Client(
            @Value("${jobforge.storage.s3.endpoint:}") String endpoint,
            @Value("${jobforge.storage.s3.region:ap-south-1}") String region,
            @Value("${jobforge.storage.s3.access-key}") String accessKey,
            @Value("${jobforge.storage.s3.secret-key}") String secretKey) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
