package com.raon.tikitaka.application.review;

/**
 * AI 응답 형식이 깨진 경우. 점수가 없거나 0~100 밖이거나 JSON으로 읽을 수 없는 응답이다.
 * 같은 요청을 다시 보내면 정상 응답이 오는 경우가 많아 재시도 대상으로 취급한다.
 */
public class AiReviewInvalidResponseException extends AiReviewRetryableException {

    public AiReviewInvalidResponseException(String message) {
        super(message, null, null);
    }

    public AiReviewInvalidResponseException(String message, Throwable cause) {
        super(message, null, cause);
    }
}
