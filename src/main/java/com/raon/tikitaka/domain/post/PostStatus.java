package com.raon.tikitaka.domain.post;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 게시물 AI 심사 상태. 피드·좋아요·신고는 DONE만 대상으로 하고,
 * 상세는 작성자 본인과 관리자만 DONE이 아닌 글을 볼 수 있다.
 * 점수·포인트·랭킹은 DONE이 되는 순간에만 반영된다.
 */
@Getter
@RequiredArgsConstructor
public enum PostStatus {

    PENDING("심사 대기"),
    DONE("심사 완료"),
    REJECTED("심사 거절"),       // Gemini가 4xx로 거절한 글. 재시도하지 않는다
    FAILED("심사 실패"),         // 일시 오류가 재시도 한도를 넘긴 글. 관리자가 재심사할 수 있다
    EXPIRED("심사 만료");        // 시즌 확정 시점까지 심사가 끝나지 않아 무효 처리된 글

    private final String description;
}
