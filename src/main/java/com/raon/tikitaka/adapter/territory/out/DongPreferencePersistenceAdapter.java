package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.application.territory.out.DongPreferenceRepositoryPort;
import com.raon.tikitaka.domain.territory.DongPreference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DongPreferencePersistenceAdapter implements DongPreferenceRepositoryPort {

    private final DongPreferenceJpaRepository dongPreferenceJpaRepository;

    @Override
    public List<DongPreference> findAllByUserId(UUID userId) {
        return dongPreferenceJpaRepository.findAllByUserIds(List.of(userId));
    }

    /** 유저 목록이 비면 빈 IN ()을 만들지 않도록 쿼리 없이 빈 목록을 준다 */
    @Override
    public List<DongPreference> findAllByUserIds(Collection<UUID> userIds) {
        return userIds.isEmpty() ? List.of() : dongPreferenceJpaRepository.findAllByUserIds(userIds);
    }

    @Override
    public void saveAll(List<DongPreference> preferences) {
        dongPreferenceJpaRepository.saveAll(preferences);
    }

    /** 본진을 옮기는 유저들의 희망 동을 한 번에 지운다. 대상이 없으면 쿼리를 보내지 않는다 */
    @Override
    public void deleteAllByUserIds(Collection<UUID> userIds) {
        if (!userIds.isEmpty()) {   // 빈 IN ()을 만들지 않는다
            dongPreferenceJpaRepository.deleteAllByUserIds(userIds);
        }
    }
}
