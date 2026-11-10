package com.raon.tikitaka.application.storage.in;

import com.raon.tikitaka.application.storage.ViewUrl;

import java.net.URL;
import java.util.Collection;
import java.util.List;

public interface StorageUseCase {

    URL issueUploadUrl(String fileName);

    URL issueViewUrl(String key);

    /**
     * 여러 key의 조회 URL을 한 번에 발급한다. 유효 시간은 issueViewUrl과 같다.
     * 요청 순서를 유지하고 중복 key는 한 번만 발급한다. key가 0개이거나 50개를 넘거나 빈 key가 섞여 있으면 400이다.
     */
    List<ViewUrl> issueViewUrls(Collection<String> keys);

    String uploadImage(byte[] content, String fileName, String contentType);
}
