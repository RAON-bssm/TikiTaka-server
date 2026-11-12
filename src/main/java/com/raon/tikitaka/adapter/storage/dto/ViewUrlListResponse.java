package com.raon.tikitaka.adapter.storage.dto;

import com.raon.tikitaka.application.storage.ViewUrl;

import java.util.List;

/**
 * 일괄 조회 URL 응답. 요청한 순서대로(중복 제거 뒤) key와 URL을 짝지어 담는다.
 * key는 URL에서 잘라내지 않고 요청받은 값을 그대로 쓴다. key에 '/'가 있어도 앱이 보낸 key와 같다.
 */
public record ViewUrlListResponse(List<Item> urls) {

    public record Item(String key, String url) {
    }

    public static ViewUrlListResponse from(List<ViewUrl> viewUrls) {
        return new ViewUrlListResponse(viewUrls.stream()
                .map(viewUrl -> new Item(viewUrl.key(), viewUrl.url().toString()))
                .toList());
    }
}
