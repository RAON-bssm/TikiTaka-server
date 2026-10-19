package com.raon.tikitaka.application.block;

import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.domain.block.UserBlock;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock
    private BlockRepositoryPort blockRepositoryPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BlockService blockService;

    private final UUID blockerId = UUID.randomUUID();
    private final UUID blockedId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        blockService = new BlockService(blockRepositoryPort, eventPublisher);
    }

    // ===== 차단 =====

    @Test
    void 차단_정상_처리하고_알림_이벤트를_발행한다() {
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        Users blocked = user(blockedId, "대상", UserStatus.ACTIVE);
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.of(blocker));
        when(blockRepositoryPort.findUser(blockedId)).thenReturn(Optional.of(blocked));
        when(blockRepositoryPort.existsByBlockerAndBlocked(blockerId, blockedId)).thenReturn(false);
        when(blockRepositoryPort.save(any(UserBlock.class))).thenAnswer(inv -> withId(inv.getArgument(0), 7L));

        blockService.block(blockerId, blockedId);

        ArgumentCaptor<UserBlock> saved = ArgumentCaptor.forClass(UserBlock.class);
        verify(blockRepositoryPort).save(saved.capture());
        assertThat(saved.getValue().getBlocker()).isSameAs(blocker);
        assertThat(saved.getValue().getBlocked()).isSameAs(blocked);

        ArgumentCaptor<BlockSubmittedEvent> captor = ArgumentCaptor.forClass(BlockSubmittedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        BlockSubmittedEvent event = captor.getValue();
        assertThat(event.blockId()).isEqualTo(7L);
        assertThat(event.blockerName()).isEqualTo("차단자");
        assertThat(event.blockerId()).isEqualTo(blockerId.toString());
        assertThat(event.blockedName()).isEqualTo("대상");
        assertThat(event.blockedId()).isEqualTo(blockedId.toString());
        assertThat(event.createdAt()).isNotNull();
    }

    @Test
    void 이미_차단_중이면_저장도_이벤트도_없이_성공한다() {
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.of(blocker));
        Users blocked = user(blockedId, "대상", UserStatus.ACTIVE);
        when(blockRepositoryPort.findUser(blockedId)).thenReturn(Optional.of(blocked));
        when(blockRepositoryPort.existsByBlockerAndBlocked(blockerId, blockedId)).thenReturn(true);

        blockService.block(blockerId, blockedId);

        verify(blockRepositoryPort, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 자기_자신_차단은_400() {
        assertStatus(() -> blockService.block(blockerId, blockerId), HttpStatus.BAD_REQUEST);
        verify(blockRepositoryPort, never()).save(any());
    }

    @Test
    void 없는_유저_차단은_404() {
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.of(blocker));
        when(blockRepositoryPort.findUser(blockedId)).thenReturn(Optional.empty());

        assertStatus(() -> blockService.block(blockerId, blockedId), HttpStatus.NOT_FOUND);
        verify(blockRepositoryPort, never()).save(any());
    }

    @Test
    void 탈퇴한_유저_차단은_404() {
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.of(blocker));
        Users withdrawn = user(blockedId, "탈퇴한 사용자_x", UserStatus.WITHDRAWN);
        when(blockRepositoryPort.findUser(blockedId)).thenReturn(Optional.of(withdrawn));

        assertStatus(() -> blockService.block(blockerId, blockedId), HttpStatus.NOT_FOUND);
        verify(blockRepositoryPort, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 요청자가_DB에_없으면_401() {
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.empty());

        assertStatus(() -> blockService.block(blockerId, blockedId), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> blockService.unblock(blockerId, blockedId), HttpStatus.UNAUTHORIZED);
    }

    // ===== 해제 =====

    @Test
    void 대상이_정상이면_차단한_적이_없어도_반복해도_성공한다() {
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        Users blocked = user(blockedId, "대상", UserStatus.ACTIVE);
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.of(blocker));
        when(blockRepositoryPort.findUser(blockedId)).thenReturn(Optional.of(blocked));

        blockService.unblock(blockerId, blockedId);
        blockService.unblock(blockerId, blockedId);

        verify(blockRepositoryPort, times(2)).deleteByBlockerAndBlocked(blockerId, blockedId);
    }

    @Test
    void 없는_유저_해제는_404() {
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.of(blocker));
        when(blockRepositoryPort.findUser(blockedId)).thenReturn(Optional.empty());

        assertStatus(() -> blockService.unblock(blockerId, blockedId), HttpStatus.NOT_FOUND);
        verify(blockRepositoryPort, never()).deleteByBlockerAndBlocked(any(), any());
    }

    @Test
    void 탈퇴한_유저_해제는_404() {
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        Users withdrawn = user(blockedId, "탈퇴한 사용자_x", UserStatus.WITHDRAWN);
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.of(blocker));
        when(blockRepositoryPort.findUser(blockedId)).thenReturn(Optional.of(withdrawn));

        assertStatus(() -> blockService.unblock(blockerId, blockedId), HttpStatus.NOT_FOUND);
        verify(blockRepositoryPort, never()).deleteByBlockerAndBlocked(any(), any());
    }

    @Test
    void 자기_자신_해제는_유저_조회보다_먼저_400() {
        assertStatus(() -> blockService.unblock(blockerId, blockerId), HttpStatus.BAD_REQUEST);
        verify(blockRepositoryPort, never()).findUser(any());
        verify(blockRepositoryPort, never()).deleteByBlockerAndBlocked(any(), any());
    }

    @Test
    void 해제에서_요청자_401이_대상_404보다_먼저다() {
        when(blockRepositoryPort.findUser(blockerId)).thenReturn(Optional.empty());

        assertStatus(() -> blockService.unblock(blockerId, blockedId), HttpStatus.UNAUTHORIZED);
        verify(blockRepositoryPort, never()).findUser(blockedId);
    }

    // ===== 목록 =====

    @Test
    void 차단_목록은_대상_정보와_차단_시각을_담는다() {
        Users blocked = user(blockedId, "대상", UserStatus.ACTIVE);
        Users blocker = user(blockerId, "차단자", UserStatus.ACTIVE);
        UserBlock block = UserBlock.of(blocker, blocked);
        LocalDateTime createdAt = block.getCreatedAt();
        when(blockRepositoryPort.findAllByBlocker(blockerId)).thenReturn(List.of(block));

        List<BlockedUser> result = blockService.getBlockedUsers(blockerId);

        assertThat(result).containsExactly(new BlockedUser(blockedId, "대상", createdAt));
    }

    // ===== helpers =====

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }

    private static Users user(UUID id, String name, UserStatus status) {
        Users user = mock(Users.class);
        // 검증 실패 경로에서는 일부 getter가 불리지 않으므로 lenient로 둔다
        lenient().when(user.getUserId()).thenReturn(id);
        lenient().when(user.getUserName()).thenReturn(name);
        lenient().when(user.getStatus()).thenReturn(status);
        return user;
    }

    private static UserBlock withId(UserBlock block, Long id) {
        ReflectionTestUtils.setField(block, "id", id);
        return block;
    }
}
