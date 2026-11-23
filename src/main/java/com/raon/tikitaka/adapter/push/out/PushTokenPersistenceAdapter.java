package com.raon.tikitaka.adapter.push.out;

import com.raon.tikitaka.application.push.out.PushTokenPort;
import com.raon.tikitaka.domain.enums.AppPlatform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PushTokenPersistenceAdapter implements PushTokenPort {

    private final PushTokenJpaRepository repository;

    @Override
    public void upsert(UUID userId, String token, AppPlatform platform, LocalDateTime now) {
        repository.upsert(userId, token, platform.name(), now);
    }

    @Override
    public void delete(UUID userId, String token) {
        repository.deleteByUserIdAndToken(userId, token);
    }

    @Override
    public List<String> findTokensByUserIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return repository.findTokensByUserIdIn(userIds);
    }

    @Override
    public void deleteAllByTokens(Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        repository.deleteByTokenIn(tokens);
    }

    @Override
    public int deleteNotUpdatedSince(LocalDateTime threshold) {
        return repository.deleteByUpdatedAtBefore(threshold);
    }
}
