package com.jobforge.backend.storage.infra;

import com.jobforge.backend.storage.facade.ObjectStorage;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** S3-compatible object storage adapter for AWS S3 and MinIO. */
@Component
@ConditionalOnProperty(name = "jobforge.storage.provider", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final String bucket;
    private final String region;

    public S3ObjectStorage(S3Client client,
            @org.springframework.beans.factory.annotation.Value("${jobforge.storage.s3.bucket}") String bucket,
            @org.springframework.beans.factory.annotation.Value("${jobforge.storage.s3.region:ap-south-1}") String region) {
        this.client = Objects.requireNonNull(client);
        this.bucket = Objects.requireNonNull(bucket);
        this.region = Objects.requireNonNull(region);
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception missing) {
            if (missing.statusCode() != 404) {
                throw missing;
            }
            var request = CreateBucketRequest.builder().bucket(bucket);
            if (!"us-east-1".equalsIgnoreCase(region)) {
                request.createBucketConfiguration(CreateBucketConfiguration.builder()
                        .locationConstraint(region).build());
            }
            client.createBucket(request.build());
        }
    }

    @Override
    public void store(String key, byte[] content) {
        Objects.requireNonNull(content, "content");
        client.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentLength((long) content.length).contentType("application/octet-stream").build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] load(String key) {
        return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
