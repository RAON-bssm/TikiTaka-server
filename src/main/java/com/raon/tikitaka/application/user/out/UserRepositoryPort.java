package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.user.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    Optional<User> findByIdWithLocations(UUID userId);
}
