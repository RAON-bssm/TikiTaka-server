package com.raon.tikitaka.application.post.review;

/**
 * 심사 한 건을 처리한 결과. 로그와 테스트에서 쓴다.
 */
public enum ReviewOutcome {
    DONE,       // 심사 통과, 점수·포인트·랭킹 반영
    RETRY,      // 일시 오류, 백오프 후 다시 시도
    FAILED,     // 일시 오류가 시도 한도에 닿음
    REJECTED,   // Gemini가 4xx로 거절
    EXPIRED,    // 시즌이 이미 확정돼 AI를 부르지 않고 무효 처리
    DEFERRED,   // 포인트 키를 기다리다 409. 실패로 세지 않고 다음 틱에 다시 선점
    SKIPPED     // 처리 사이에 삭제됐거나 이미 PENDING이 아니어서 아무것도 하지 않음
}
