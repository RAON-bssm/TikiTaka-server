package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 사용자별 게시물 업로드 한도. application.yaml의 post-upload-limit 블록과 매핑된다.
 * 비동기 심사(post-review.async)와 독립이라 동기 방식에서도 켜면 적용된다.
 *
 * @param enabled    한도 전체를 켜고 끈다. 꺼져 있으면 검사도 쿼리도 하지 않는다
 * @param maxCount   window 안에 올릴 수 있는 최대 글 수. PENDING, DONE, REJECTED 글과 삭제한 글을 센다
 * @param window     업로드 횟수를 세는 기간. 지금부터 거슬러 올라가는 슬라이딩 윈도우다
 * @param maxPending 한 사용자의 심사 대기(삭제하지 않은 PENDING) 글 최대 수
 */
@ConfigurationProperties(prefix = "post-upload-limit")
public record PostUploadLimitProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("2") int maxCount,
        @DefaultValue("10m") Duration window,
        @DefaultValue("2") int maxPending
) {
}
