package com.raon.tikitaka.application.review;

import java.time.Duration;

/**
 * 다시 시도하면 성공할 수 있는 AI 심사 실패. 429, 5xx, 타임아웃, 네트워크 오류가 여기에 든다.
 * 워커는 이 예외를 받으면 글을 PENDING으로 두고 백오프 후 다시 심사한다.
 * retryAfter는 Gemini가 다시 시도할 시점을 알려줬을 때만 담기고 없으면 null이다.
 */
public class AiReviewRetryableException extends RuntimeException {

    private final Duration retryAfter;

    public AiReviewRetryableException(String message, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
