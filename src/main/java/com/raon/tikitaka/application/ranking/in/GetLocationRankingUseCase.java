package com.raon.tikitaka.application.ranking.in;

import com.raon.tikitaka.application.ranking.LocationRankRow;

import java.util.List;

public interface GetLocationRankingUseCase {

    /**
     * 현재 진행 중인 라운드의 지역 랭킹. 미참여 동네도 0점으로 포함하고 동점은 같은 등수다.
     */
    List<LocationRankRow> getLocationRanking();
}
