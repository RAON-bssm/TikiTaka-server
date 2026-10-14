package com.raon.tikitaka.application.report.in;

import com.raon.tikitaka.domain.enums.ReportReason;

import java.util.UUID;

public interface ReportUserUseCase {

    /**
     * 유저를 신고하고 생성된 신고 id를 돌려준다. 같은 유저는 신고자당 한 번만 신고할 수 있다.
     * FAKE_MISSION은 게시물에만 해당하는 사유라 받지 않는다.
     */
    Long reportUser(UUID reporterId, UUID reportedUserId, ReportReason reason, String detail);
}
