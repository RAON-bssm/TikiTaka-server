package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<Users> findById(UUID userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public Optional<Users> findByProviderAndProviderId(LoginProvider provider, String providerId) {
        return userJpaRepository.findByProviderAndProviderId(provider, providerId);
    }

    @Override
    public boolean existsByUserName(String userName) {
        return userJpaRepository.existsByUserName(userName);
    }

    @Override
    public Users save(Users user) {
        return userJpaRepository.save(user);
    }
}
