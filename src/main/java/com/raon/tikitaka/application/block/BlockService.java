package com.raon.tikitaka.application.block;

import com.raon.tikitaka.application.block.in.BlockUserUseCase;
import com.raon.tikitaka.application.block.in.GetBlockedUsersUseCase;
import com.raon.tikitaka.application.block.in.UnblockUserUseCase;
import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.domain.block.UserBlock;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService implements BlockUserUseCase, UnblockUserUseCase, GetBlockedUsersUseCase {

    private final BlockRepositoryPort blockRepositoryPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        validateNotSelf(blockerId, blockedId);
        Users blocker = getRequester(blockerId);
        Users blocked = getTarget(blockedId);

        // 이미 차단 중이면 아무것도 하지 않는다. uk_user_block_blocker_blocked는 동시성 상황의
        // 이중 삽입을 막는 최후 방어선일 뿐, 정상 흐름은 여기서 먼저 걸러낸다
        if (blockRepositoryPort.existsByBlockerAndBlocked(blockerId, blockedId)) {
            return;
        }
        UserBlock block = blockRepositoryPort.save(UserBlock.of(blocker, blocked));

        // 새로 만든 차단만 알린다. 리스너는 커밋 후 다른 스레드에서 돌기 때문에 필요한 값은 여기서 미리 꺼낸다
        eventPublisher.publishEvent(new BlockSubmittedEvent(
                block.getId(), blocker.getUserName(), blockerId.toString(),
                blocked.getUserName(), blockedId.toString(), block.getCreatedAt()));
    }

    @Override
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        validateNotSelf(blockerId, blockedId);
        getRequester(blockerId);
        getTarget(blockedId);

        // 대상이 존재하는 한, 차단한 적이 없어도 delete는 0건 삭제로 끝나므로 그 자체로 idempotent하다
        blockRepositoryPort.deleteByBlockerAndBlocked(blockerId, blockedId);
    }

    @Override
    public List<BlockedUser> getBlockedUsers(UUID blockerId) {
        List<BlockedUser> result = new ArrayList<>();
        for (UserBlock block : blockRepositoryPort.findAllByBlocker(blockerId)) {
            Users blocked = block.getBlocked();
            result.add(new BlockedUser(blocked.getUserId(), blocked.getUserName(), block.getCreatedAt()));
        }
        return result;
    }

    private static void validateNotSelf(UUID blockerId, UUID blockedId) {
        if (blockedId.equals(blockerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없습니다.");
        }
    }

    /**
     * 차단·해제 대상. 없거나 탈퇴한 유저는 404로 끊는다.
     */
    private Users getTarget(UUID targetId) {
        return blockRepositoryPort.findUser(targetId)
                .filter(user -> user.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    /**
     * 토큰은 유효한데 유저가 없는 경우. ReportService.getReporter와 같은 기준으로 401을 낸다.
     */
    private Users getRequester(UUID requesterId) {
        return blockRepositoryPort.findUser(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }
}
