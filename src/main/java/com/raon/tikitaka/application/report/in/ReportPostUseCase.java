package com.raon.tikitaka.application.report.in;

import com.raon.tikitaka.domain.enums.ReportReason;

import java.util.UUID;

public interface ReportPostUseCase {

    /**
     * 게시물을 신고하고 생성된 신고 id를 돌려준다. 같은 게시물은 유저당 한 번만 신고할 수 있다.
     * 관리자 알림 메일은 커밋 이후 비동기로 나가므로 발송 실패가 이 호출의 결과에 영향을 주지 않는다.
     */
    Long reportPost(UUID reporterId, UUID postId, ReportReason reason, String detail);
}
