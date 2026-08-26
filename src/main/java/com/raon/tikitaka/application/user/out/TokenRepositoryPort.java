package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.token.Token;

import java.util.Optional;
import java.util.UUID;

public interface TokenRepositoryPort {

    Optional<Token> findByUserId(UUID userId);

    void save(Token token);

    void deleteByUserId(UUID userId);
}
