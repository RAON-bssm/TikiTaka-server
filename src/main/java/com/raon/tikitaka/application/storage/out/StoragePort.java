package com.raon.tikitaka.application.storage.out;

import com.raon.tikitaka.application.storage.StoredImage;

import java.net.URL;
import java.time.Duration;

public interface StoragePort {

    URL issueUploadUrl(String key, Duration expiration);

    URL issueViewUrl(String key, Duration expiration);

    void upload(String key, byte[] content, String contentType);

    /**
     * 저장된 이미지를 읽는다. 게시물 심사 워커가 업로드 요청이 끝난 뒤 이미지를 다시 읽을 때 쓴다.
     */
    StoredImage download(String key);
}
