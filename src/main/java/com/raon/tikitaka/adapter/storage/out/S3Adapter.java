package com.raon.tikitaka.adapter.storage.out;

import com.raon.tikitaka.application.storage.out.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class S3Adapter implements StoragePort {

    private static final Duration SERVER_UPLOAD_SIGNATURE_DURATION = Duration.ofSeconds(30);

    private final S3Presigner s3Presigner;
    private final WebClient webClient = WebClient.builder().build();

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public URL issueUploadUrl(String key, Duration expiration) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url();
    }

    @Override
    public URL issueViewUrl(String key, Duration expiration) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(objectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url();
    }

    @Override
    public void upload(String key, byte[] content, String contentType) {
        URL presignedUrl = issueUploadUrl(key, SERVER_UPLOAD_SIGNATURE_DURATION);
        MediaType mediaType = contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;

        webClient.put()
                .uri(URI.create(presignedUrl.toString()))
                .contentType(mediaType)
                .bodyValue(content)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
