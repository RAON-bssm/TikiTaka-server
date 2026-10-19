package com.raon.tikitaka.application.block.in;

import com.raon.tikitaka.application.block.BlockedUser;

import java.util.List;
import java.util.UUID;

public interface GetBlockedUsersUseCase {

    /**
     * 내가 차단한 유저 목록. 최신순이고 탈퇴한 유저는 빠진다.
     */
    List<BlockedUser> getBlockedUsers(UUID blockerId);
}
