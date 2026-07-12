package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.user.Users;

import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    Optional<Users> findByIdWithLocations(UUID userId);
}
