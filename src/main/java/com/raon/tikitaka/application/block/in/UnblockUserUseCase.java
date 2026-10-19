package com.raon.tikitaka.application.block.in;

import java.util.UUID;

public interface UnblockUserUseCase {

    /**
     * 차단을 해제한다. 차단한 적이 없어도 성공한다(idempotent).
     */
    void unblock(UUID blockerId, UUID blockedId);
}
