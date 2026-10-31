package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.Territory;

import java.util.List;

/** 지도 조회. 동, 주인, 주인 팀을 함께 가져온다 */
public interface TerritoryQueryPort {

    List<Territory> findAllByCycleId(Long cycleId);

    List<Territory> findAllByCycleIdAndLocationId(Long cycleId, Long locationId);
}
