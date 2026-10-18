package com.raon.tikitaka.application.storage;

import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.storage.out.StoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService implements StorageUseCase {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(3);
    private static final Duration VIEW_URL_EXPIRATION = Duration.ofMinutes(10);

    private final StoragePort storagePort;

    @Override
    public URL issueUploadUrl(String fileName) {
        String key = UUID.randomUUID() + "-" + fileName;
        return storagePort.issueUploadUrl(key, UPLOAD_URL_EXPIRATION);
    }

    @Override
    public URL issueViewUrl(String key) {
        return storagePort.issueViewUrl(key, VIEW_URL_EXPIRATION);
    }

    @Override
    public String uploadImage(byte[] content, String fileName, String contentType) {
        String key = UUID.randomUUID() + "-" + fileName;
        storagePort.upload(key, content, contentType);
        return key;
    }

    @Override
    public void deleteImageQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException e) {
            // S3 삭제 권한이 없거나 일시 장애여도 호출한 쪽의 원래 예외가 우선이다. 남은 이미지는 key로 추적할 수 있게 남긴다
            log.warn("S3 이미지 정리에 실패했습니다. key={}", key, e);
        }
    }
}
