package com.jobforge.backend.storage.infra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Testcontainers(disabledWithoutDocker = true)
class S3ObjectStorageIT {

    private static final String ACCESS_KEY = "jobforge-test";
    private static final String SECRET_KEY = "jobforge-test-secret-123456";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse("quay.io/minio/minio:latest"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withExposedPorts(9000)
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).forStatusCode(200));

    @Test
    void storesLoadsAndDeletesObjectsThroughMinioS3Api() {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            String bucket = "jobforge-test-" + UUID.randomUUID().toString().substring(0, 8);
            S3ObjectStorage storage = new S3ObjectStorage(client, bucket, Region.US_EAST_1.id());
            byte[] content = "private resume test bytes".getBytes(StandardCharsets.UTF_8);
            String key = "resumes/test-user/resume.txt";

            storage.store(key, content);
            assertArrayEquals(content, storage.load(key));
            storage.delete(key);
        }
    }
}
