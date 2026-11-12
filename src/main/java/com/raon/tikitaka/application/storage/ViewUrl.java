package com.raon.tikitaka.application.storage;

import java.net.URL;

/**
 * 조회용 presigned URL과 그 URL을 만든 S3 key. URL에서 key를 거꾸로 잘라내지 않도록 요청받은 key를 그대로 둔다.
 */
public record ViewUrl(String key, URL url) {
}
