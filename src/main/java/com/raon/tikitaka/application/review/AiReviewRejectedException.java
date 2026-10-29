package com.raon.tikitaka.application.review;

/**
 * 다시 보내도 결과가 같은 AI 심사 실패. Gemini가 429를 뺀 4xx로 응답한 경우다(콘텐츠 정책 차단 등).
 * 워커는 이 예외를 받으면 재시도하지 않고 글을 REJECTED로 바꾼다.
 */
public class AiReviewRejectedException extends RuntimeException {

    private final int status;

    public AiReviewRejectedException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
