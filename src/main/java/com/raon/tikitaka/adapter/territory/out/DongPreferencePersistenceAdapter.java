package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.DongPreferenceRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DongPreferencePersistenceAdapter implements DongPreferenceRepositoryPort {

    private final DongPreferenceJpaRepository dongPreferenceJpaRepository;

    @Override
    public void deleteAllByUserIds(Collection<UUID> userIds) {
        if (!userIds.isEmpty()) {   // 빈 IN ()을 만들지 않는다
            dongPreferenceJpaRepository.deleteAllByUserIds(userIds);
        }
    }
}
