package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.RepresentativesView;
import com.raon.tikitaka.application.territory.view.TerritoryDongsView;

import java.util.UUID;

public interface QueryTerritoryUseCase {

    CurrentCycleView getCurrentCycle(UUID userId);

    /** locationId가 null이면 보드 전체 */
    TerritoryDongsView getDongs(UUID userId, Long locationId);

    /**
     * 대표 선발 결과. 임시 API, 프론트 확정 후 변경될 수 있음.
     * cycleId가 null이면 현재 사이클, locationId가 null이면 보드 전체.
     */
    RepresentativesView getRepresentatives(UUID userId, Long cycleId, Long locationId);
}
