package com.raon.tikitaka.application.storage.in;

import java.net.URL;

public interface StorageUseCase {

    URL issueUploadUrl(String fileName);

    URL issueViewUrl(String key);

    String uploadImage(byte[] content, String fileName, String contentType);
}
