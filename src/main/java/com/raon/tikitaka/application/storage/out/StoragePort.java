package com.raon.tikitaka.application.storage.out;

import java.net.URL;
import java.time.Duration;

public interface StoragePort {

    URL issueUploadUrl(String key, Duration expiration);

    URL issueViewUrl(String key, Duration expiration);
}
