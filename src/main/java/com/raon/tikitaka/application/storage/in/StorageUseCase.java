package com.raon.tikitaka.application.storage.in;

import com.raon.tikitaka.application.storage.UploadedImage;
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

    /**
     * 원본을 올리고 목록용 썸네일(JPEG)도 만들어 올린다. 원본 업로드가 실패하면 예외지만, 썸네일은 만들거나 올리지 못해도
     * 예외 없이 thumbnailKey가 null이다. 썸네일 때문에 업로드가 실패하지 않게 하기 위해서다.
     */
    UploadedImage uploadImageWithThumbnail(byte[] content, String fileName, String contentType);
}
