package com.raon.tikitaka.application.report;

import com.raon.tikitaka.domain.enums.ReportReason;

import java.time.LocalDateTime;

/**
 * 신고 접수 이벤트. 리스너는 커밋 이후 별도 스레드에서 돌아 영속성 컨텍스트가 없으므로
 * 메일에 필요한 값을 트랜잭션 안에서 전부 문자열로 풀어 담는다.
 * 게시물 신고가 아니면 postAuthor*, board* 필드는 null이다.
 */
public record ReportSubmittedEvent(
        Long reportId,
        TargetType targetType,
        String targetId,
        ReportReason reason,
        String detail,
        String reporterName,
        String reporterId,
        LocalDateTime createdAt,
        String postAuthorName,
        Long boardId,
        String boardMission
) {

    public enum TargetType {
        POST("게시물"),
        USER("유저");

        private final String label;

        TargetType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
