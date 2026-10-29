package com.raon.tikitaka.application.review;

/**
 * 다시 보내도 결과가 같은 AI 심사 실패. Gemini가 4xx(429, 401, 403, 408 제외)로 응답했거나,
 * 200으로 응답했지만 안전 필터에 막힌 경우다(이때 status는 200).
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
