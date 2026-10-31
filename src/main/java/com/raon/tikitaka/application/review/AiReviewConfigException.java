package com.raon.tikitaka.application.review;

/**
 * 서버 설정 때문에 생긴 AI 심사 실패. Gemini가 404로 응답한 경우(모델 이름 오류 등)다.
 * 설정을 고치기 전까지는 다시 보내도 같으므로 재시도하지 않는다. 하지만 글 내용 탓이 아니므로 거절(REJECTED)이 아니라
 * 바로 FAILED로 끝내서, 설정을 고친 뒤 관리자 재심사로 되살릴 수 있게 한다.
 */
public class AiReviewConfigException extends RuntimeException {

    private final int status;

    public AiReviewConfigException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
