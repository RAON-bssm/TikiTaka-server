package com.raon.tikitaka.adapter.block;

import com.raon.tikitaka.application.block.AlreadyBlockedException;
import com.raon.tikitaka.application.block.in.BlockUserUseCase;
import com.raon.tikitaka.application.block.in.GetBlockedUsersUseCase;
import com.raon.tikitaka.application.block.in.UnblockUserUseCase;
import com.raon.tikitaka.global.guard.RequestGuard;
import com.raon.tikitaka.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 유스케이스는 mock이고 가드는 실제 객체다.
 */
class BlockControllerTest {

    private final BlockUserUseCase blockUserUseCase = mock(BlockUserUseCase.class);
    private final UnblockUserUseCase unblockUserUseCase = mock(UnblockUserUseCase.class);
    private BlockController controller;

    private final UUID blockerId = UUID.randomUUID();
    private final UUID blockedId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new BlockController(blockUserUseCase, unblockUserUseCase, mock(GetBlockedUsersUseCase.class),
                new RequestGuard());
    }

    @Test
    void 차단과_해제는_바디_status도_200() {
        ApiResponse<Void> blocked = controller.block(blockerId, blockedId);
        ApiResponse<Void> unblocked = controller.unblock(blockerId, blockedId);

        assertThat(blocked.status()).isEqualTo(200);
        assertThat(blocked.message()).isEqualTo("차단했습니다");
        assertThat(unblocked.status()).isEqualTo(200);
        assertThat(unblocked.message()).isEqualTo("차단을 해제했습니다");
        verify(blockUserUseCase).block(blockerId, blockedId);
        verify(unblockUserUseCase).unblock(blockerId, blockedId);
    }

    @Test
    void 동시_요청으로_유니크_제약에_걸려도_차단은_성공으로_응답한다() {
        doThrow(new AlreadyBlockedException()).when(blockUserUseCase).block(blockerId, blockedId);

        ApiResponse<Void> response = controller.block(blockerId, blockedId);

        assertThat(response.status()).isEqualTo(200);
    }
}
