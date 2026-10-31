package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.TeamLandResult;

import java.util.List;

public interface TeamLandResultPort {

    void saveAll(List<TeamLandResult> results);

    /** 순위 순서, 구 함께 조회 */
    List<TeamLandResult> findAllByCycleId(Long cycleId);
}
