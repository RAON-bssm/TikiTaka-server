package com.raon.tikitaka.application.block.in;

import java.util.UUID;

public interface BlockUserUseCase {

    /**
     * blocker가 blocked를 차단한다. 이미 차단 중이면 아무 것도 하지 않는다(idempotent).
     * 새로 차단했을 때만 관리자 알림 메일이 커밋 이후 비동기로 나간다.
     */
    void block(UUID blockerId, UUID blockedId);
}
