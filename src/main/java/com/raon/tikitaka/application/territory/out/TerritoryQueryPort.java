package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.Representative;
import com.raon.tikitaka.domain.territory.Territory;

import java.util.List;
import java.util.UUID;

/** 지도·선발 결과 조회. 동, 주인, 주인 팀을 함께 가져온다 */
public interface TerritoryQueryPort {

    List<Territory> findAllByCycleId(Long cycleId);

    List<Territory> findAllByCycleIdAndLocationId(Long cycleId, Long locationId);

    /**
     * 사이클의 대표를 구, 순위 순으로. locationId가 null이면 보드 전체.
     * 탈퇴 유저와 viewerId가 차단한 유저는 뺀다. 유저와 배정 동을 함께 가져온다.
     */
    List<Representative> findRepresentatives(Long cycleId, Long locationId, UUID viewerId);
}
