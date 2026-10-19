package com.raon.tikitaka.adapter.block.dto;

import com.raon.tikitaka.application.block.BlockedUser;

import java.time.LocalDateTime;
import java.util.UUID;

public record BlockedUserResponse(UUID userId, String userName, LocalDateTime blockedAt) {

    public static BlockedUserResponse from(BlockedUser blockedUser) {
        return new BlockedUserResponse(blockedUser.userId(), blockedUser.userName(), blockedUser.blockedAt());
    }
}
