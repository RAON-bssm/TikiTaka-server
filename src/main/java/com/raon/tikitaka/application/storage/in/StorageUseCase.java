package com.raon.tikitaka.application.storage.in;

import java.net.URL;

public interface StorageUseCase {

    URL issueUploadUrl(String fileName);

    URL issueViewUrl(String key);
}
