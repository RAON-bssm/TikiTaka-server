package com.raon.tikitaka.adapter.report.dto;

import com.raon.tikitaka.domain.enums.ReportReason;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 신고 요청. reason을 enum이 아닌 문자열로 받는 이유는, enum으로 받으면 잘못된 값이
 * Jackson 역직렬화 단계에서 실패해 공통 응답 형식이 아닌 스프링 기본 400 바디로 나가기 때문이다.
 */
public record ReportRequest(String reason, String detail) {

    public ReportReason toReason() {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 사유는 필수입니다.");
        }
        try {
            return ReportReason.valueOf(reason);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바르지 않은 신고 사유입니다: " + reason);
        }
    }
}
