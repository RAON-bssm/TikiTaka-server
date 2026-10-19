package com.raon.tikitaka.adapter.block;

import com.raon.tikitaka.adapter.block.dto.BlockedUserListResponse;
import com.raon.tikitaka.application.block.AlreadyBlockedException;
import com.raon.tikitaka.application.block.in.BlockUserUseCase;
import com.raon.tikitaka.application.block.in.GetBlockedUsersUseCase;
import com.raon.tikitaka.application.block.in.UnblockUserUseCase;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 유저 차단 API. /api/user/**는 SecurityConfig에서 열어두지 않았으므로 전부 인증이 필요하다.
 *
 * {userId}는 UUID 패턴으로 제한한다. /api/user/me/blocks와는 세그먼트 수가 달라 원래 겹치지 않지만,
 * /api/user/me/block 같은 요청이 UUID 변환 실패(스프링 기본 400 바디)로 새지 않고 매핑 없음으로 끝나게 하기 위해서다.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class BlockController {

    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final String BLOCK_BUSY_MESSAGE = "이전 차단 요청을 처리하는 중이에요.";

    private final BlockUserUseCase blockUserUseCase;
    private final UnblockUserUseCase unblockUserUseCase;
    private final GetBlockedUsersUseCase getBlockedUsersUseCase;
    private final RequestGuard requestGuard;

    /**
     * 같은 (차단자, 대상) 쌍의 차단·해제는 같은 키를 공유하고, 겹치면 앞선 요청이 끝난 뒤 순서대로 실행한다.
     * 차단 직후 해제를 눌러도 해제가 버려지지 않고 마지막에 누른 쪽이 최종 상태가 된다.
     */
    @PostMapping("/{userId:" + UUID_PATTERN + "}/block")
    public ApiResponse<Void> block(@AuthenticationPrincipal UUID blockerId, @PathVariable UUID userId) {
        requestGuard.executeWaiting(blockKey(blockerId, userId), BLOCK_BUSY_MESSAGE, () -> {
            try {
                blockUserUseCase.block(blockerId, userId);
            } catch (AlreadyBlockedException e) {
                // 가드 밖(다른 인스턴스 등)에서 같은 차단이 먼저 커밋됐다. 결과 상태가 같으므로 성공으로 본다
            }
        });
        return ApiResponse.of(200, "차단했습니다", null);
    }

    @DeleteMapping("/{userId:" + UUID_PATTERN + "}/block")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal UUID blockerId, @PathVariable UUID userId) {
        requestGuard.executeWaiting(blockKey(blockerId, userId), BLOCK_BUSY_MESSAGE,
                () -> unblockUserUseCase.unblock(blockerId, userId));
        return ApiResponse.of(200, "차단을 해제했습니다", null);
    }

    @GetMapping("/me/blocks")
    public ApiResponse<BlockedUserListResponse> getBlockedUsers(@AuthenticationPrincipal UUID userId) {
        BlockedUserListResponse response = BlockedUserListResponse.from(getBlockedUsersUseCase.getBlockedUsers(userId));
        return ApiResponse.of(200, "차단 목록 조회 성공", response);
    }

    private static String blockKey(UUID blockerId, UUID blockedId) {
        return "block:" + blockerId + ":" + blockedId;
    }
}
