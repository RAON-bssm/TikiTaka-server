package com.raon.tikitaka.application.block.in;

import java.util.UUID;

public interface UnblockUserUseCase {

    /**
     * 차단을 해제한다. 대상이 존재할 때에 한해 idempotent라 차단한 적이 없어도 성공한다.
     * 대상 유저가 없거나 탈퇴했으면 404다.
     */
    void unblock(UUID blockerId, UUID blockedId);
}
