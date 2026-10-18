package com.raon.tikitaka.application.storage.in;

import java.net.URL;

public interface StorageUseCase {

    URL issueUploadUrl(String fileName);

    URL issueViewUrl(String key);

    String uploadImage(byte[] content, String fileName, String contentType);

    /**
     * 올려둔 이미지를 지운다. 게시물 등록이 실패했을 때 남는 이미지를 치우는 용도라 best-effort이고,
     * 삭제에 실패해도 예외를 던지지 않는다(원래의 실패 사유를 가리지 않기 위해서다).
     */
    void deleteImageQuietly(String key);
}
