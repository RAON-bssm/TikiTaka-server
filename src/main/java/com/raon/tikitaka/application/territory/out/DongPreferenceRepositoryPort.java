package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.DongPreference;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DongPreferenceRepositoryPort {

    /** 순위 순서, 동 함께 조회 */
    List<DongPreference> findAllByUserId(UUID userId);

    /** 대표 배치용. 순위 순서, 동 함께 조회 */
    List<DongPreference> findAllByUserIds(Collection<UUID> userIds);

    void saveAll(List<DongPreference> preferences);

    /** 희망 동 교체·본진 변경 시 지운다. 즉시 실행되므로 이어서 같은 순위로 저장해도 유니크 제약에 걸리지 않는다 */
    void deleteAllByUserIds(Collection<UUID> userIds);
}
