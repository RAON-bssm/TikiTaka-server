package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.TerritoryDetailView;
import com.raon.tikitaka.application.territory.view.TerritoryDongsView;

import java.util.UUID;

public interface QueryTerritoryUseCase {

    CurrentCycleView getCurrentCycle(UUID userId);

    /** locationId가 null이면 보드 전체 */
    TerritoryDongsView getDongs(UUID userId, Long locationId);

    /** 현재 사이클에서 그 동의 땅 */
    TerritoryDetailView getDongDetail(UUID userId, Long dongCode);
}
