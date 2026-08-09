package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.user.Users;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    Optional<Users> findById(UUID userId);

    Optional<Users> findByProviderAndProviderId(LoginProvider provider, String providerId);

    boolean existsByUserName(String userName);

    Users save(Users user);
}
