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
}
