package com.raon.tikitaka.adapter.block.out;

import com.raon.tikitaka.adapter.user.out.UserJpaRepository;
import com.raon.tikitaka.application.block.AlreadyBlockedException;
import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.domain.block.UserBlock;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BlockPersistenceAdapter implements BlockRepositoryPort {

    private static final String UNIQUE_CONSTRAINT_PREFIX = "uk_user_block_";

    private final UserBlockJpaRepository userBlockJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<Users> findUser(UUID userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public boolean existsByBlockerAndBlocked(UUID blockerId, UUID blockedId) {
        return userBlockJpaRepository.existsByBlocker_UserIdAndBlocked_UserId(blockerId, blockedId);
    }

    /**
     * 같은 쌍의 요청은 컨트롤러의 RequestGuard가 순서대로 처리하므로 정상적으로는 여기까지 오지 않는다.
     * 그래도 겹치면 늦은 쪽이 유니크 제약에 걸리는데, 커밋 시점까지 미루면 500이 나가므로 여기서 flush해
     * 차단 유니크 제약 위반만 AlreadyBlockedException으로 바꾼다. 전역 핸들러는 건드리지 않는다.
     */
    @Override
    public UserBlock save(UserBlock block) {
        try {
            return userBlockJpaRepository.saveAndFlush(block);
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains(UNIQUE_CONSTRAINT_PREFIX)) {
                throw new AlreadyBlockedException();
            }
            throw e;
        }
    }

    @Override
    public void deleteByBlockerAndBlocked(UUID blockerId, UUID blockedId) {
        userBlockJpaRepository.deleteByBlockerAndBlocked(blockerId, blockedId);
    }

    @Override
    public List<UserBlock> findAllByBlocker(UUID blockerId) {
        return userBlockJpaRepository.findAllByBlocker(blockerId);
    }

    @Override
    public void deleteAllInvolving(UUID userId) {
        userBlockJpaRepository.deleteAllInvolving(userId);
    }
}
