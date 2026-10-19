package com.raon.tikitaka.adapter.block.dto;

import com.raon.tikitaka.application.block.BlockedUser;

import java.util.List;

public record BlockedUserListResponse(List<BlockedUserResponse> blockedUsers) {

    public static BlockedUserListResponse from(List<BlockedUser> blockedUsers) {
        return new BlockedUserListResponse(blockedUsers.stream().map(BlockedUserResponse::from).toList());
    }
}
