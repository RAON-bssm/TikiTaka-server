package com.raon.tikitaka.application.territory.out;

import java.util.Set;
import java.util.UUID;

/** 땅 조회에서 내가 차단한 유저를 가리기 위한 조회 */
public interface BlockedUserPort {

    /** blockerId가 차단한 유저 id 목록 */
    Set<UUID> findBlockedUserIds(UUID blockerId);
}
