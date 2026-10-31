package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.Territory;

import java.util.List;
import java.util.Optional;

/** 지도·상세 조회. 동, 주인, 주인 팀을 함께 가져온다 */
public interface TerritoryQueryPort {

    List<Territory> findAllByCycleId(Long cycleId);

    List<Territory> findAllByCycleIdAndLocationId(Long cycleId, Long locationId);

    Optional<Territory> findById(Long territoryId);

    Optional<Territory> findByCycleIdAndDongCode(Long cycleId, Long dongCode);

    /** 도전 판정용. 같은 땅의 동시 도전을 한 줄로 세운다(SELECT ... FOR UPDATE) */
    Optional<Territory> findByIdForUpdate(Long territoryId);

    /** 그 사이클에서 유저가 가진 땅 수(최대 보유 수 검사) */
    long countOwnedBy(Long cycleId, java.util.UUID userId);
}
