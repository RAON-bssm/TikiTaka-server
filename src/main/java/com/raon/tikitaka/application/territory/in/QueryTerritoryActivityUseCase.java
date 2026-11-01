package com.raon.tikitaka.application.territory.in;

import com.raon.tikitaka.application.territory.view.DevelopmentHistoryView;
import com.raon.tikitaka.application.territory.view.MyTerritoriesView;

import java.util.UUID;

public interface QueryTerritoryActivityUseCase {

    /** 현재 사이클에서 내가 가진 땅 */
    MyTerritoriesView getMyTerritories(UUID userId);

    /** 현재 사이클에서 그 동의 땅 개발 이력(최신순 최대 100건) */
    DevelopmentHistoryView getDevelopments(UUID userId, Long dongCode);
}
