package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.TerritoryDongsView;

import java.util.UUID;

public interface QueryTerritoryUseCase {

    CurrentCycleView getCurrentCycle(UUID userId);

    /** locationId가 null이면 보드 전체 */
    TerritoryDongsView getDongs(UUID userId, Long locationId);
}
