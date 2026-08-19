package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<Users> findByIdWithLocations(UUID userId) {
        return userJpaRepository.findByIdWithLocations(userId);
    }

    @Override
    public List<Long> findExpectedLocationIdsOfActiveUsers() {
        return userJpaRepository.findExpectedLocationIdsOfActiveUsers();
    }

    @Override
    public List<Users> findAllWithPendingLocationSwap() {
        return userJpaRepository.findAllByPendingLocationSwapTrue();
    }

    @Override
    public List<Users> findAllActiveLastActiveBefore(LocalDateTime threshold) {
        return userJpaRepository.findAllByLastActiveAtBeforeAndStatus(threshold, UserStatus.ACTIVE);
    }
}
