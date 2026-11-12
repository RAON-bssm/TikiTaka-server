package com.raon.tikitaka.application.storage.out;

import java.util.Optional;

/**
 * 원본 이미지로 목록용 JPEG 썸네일을 만든다.
 */
public interface ThumbnailGeneratorPort {

    /**
     * 썸네일 JPEG 바이트. 만들 수 없으면(기능이 꺼짐, 이미지가 아님, 읽을 수 없는 형식, 픽셀 수 상한 초과, 처리 중 오류)
     * 비어 있다. 썸네일 때문에 업로드가 실패하면 안 되므로 예외를 던지지 않는다.
     */
    Optional<byte[]> createThumbnail(byte[] image);
}
