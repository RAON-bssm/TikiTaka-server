package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.application.user.out.TokenRepositoryPort;
import com.raon.tikitaka.domain.token.Token;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TokenPersistenceAdapter implements TokenRepositoryPort {

    private final TokenJpaRepository tokenJpaRepository;

    @Override
    public Optional<Token> findByUserId(UUID userId) {
        return tokenJpaRepository.findById(userId);
    }

    @Override
    public void save(Token token) {
        tokenJpaRepository.save(token);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        tokenJpaRepository.deleteById(userId);
    }
}
