package com.jobforge.backend.storage.infra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import com.jobforge.backend.storage.facade.ObjectStorage;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3ObjectStorageTest {

    @Test
    void delegatesStoreLoadAndDeleteToPrivateBucket() {
        S3Client client = mock(S3Client.class);
        when(client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        byte[] expected = new byte[] {1, 2, 3};
        when(client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), expected));
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

        ObjectStorage storage = new S3ObjectStorage(client, "private-uploads", "ap-south-1");
        storage.store("avatars/user-1.png", expected);
        assertArrayEquals(expected, storage.load("avatars/user-1.png"));
        storage.delete("avatars/user-1.png");

        verify(client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
        verify(client).getObjectAsBytes(any(GetObjectRequest.class));
        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }
}
