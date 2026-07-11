package com.raon.tikitaka.application.storage;

import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.storage.out.StoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.util.UUID;

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
}
