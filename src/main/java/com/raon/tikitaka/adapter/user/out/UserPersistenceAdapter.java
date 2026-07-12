package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<User> findByIdWithLocations(UUID userId) {
        return userJpaRepository.findByIdWithLocations(userId);
    }
}
