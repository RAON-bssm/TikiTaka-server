package com.raon.tikitaka.adapter.storage.dto;

import java.net.URL;

// 1. 괄호 안에 String key 필드를 추가해 줍니다.
public record PresignedUrlResponse(
        String url,
        String key
) {

    public static PresignedUrlResponse from(URL url) {
        String fullUrl = url.toString();

        String baseUrl = fullUrl.contains("?") ? fullUrl.split("\\?")[0] : fullUrl;

        String extractedKey = baseUrl.substring(baseUrl.lastIndexOf("/") + 1);

        return new PresignedUrlResponse(fullUrl, extractedKey);
    }
}