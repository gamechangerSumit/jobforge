package com.jobforge.backend.storage.infra;

import com.jobforge.backend.storage.facade.ObjectStorage;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3-compatible object storage adapter. Works with AWS S3 and MinIO's S3 API.
 * Bucket provisioning is performed once at startup; object keys remain server-generated.
 */
@Component
@ConditionalOnProperty(name = "jobforge.storage.provider", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final String bucket;

    public S3ObjectStorage(S3Client client,
            @org.springframework.beans.factory.annotation.Value("${jobforge.storage.s3.bucket}") String bucket) {
        this.client = Objects.requireNonNull(client);
        this.bucket = Objects.requireNonNull(bucket);
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException missing) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
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
