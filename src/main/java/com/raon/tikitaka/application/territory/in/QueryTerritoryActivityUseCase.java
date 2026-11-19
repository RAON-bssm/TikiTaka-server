package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.DevelopmentHistoryView;
import com.raon.tikitaka.application.territory.view.MyTerritoriesView;
import com.raon.tikitaka.application.territory.view.WageHistoryView;

import java.util.UUID;

public interface QueryTerritoryActivityUseCase {

    /** 현재 사이클에서 내가 가진 땅 */
    MyTerritoriesView getMyTerritories(UUID userId);

    /** 현재 사이클에서 그 동의 땅 개발 이력(최신순 최대 100건) */
    DevelopmentHistoryView getDevelopments(UUID userId, Long dongCode);

    /** 내 일급 지급 이력(최신순 최대 100건). cycleId가 있으면 그 사이클만, 없으면 모든 사이클 */
    WageHistoryView getWages(UUID userId, Long cycleId);
}
