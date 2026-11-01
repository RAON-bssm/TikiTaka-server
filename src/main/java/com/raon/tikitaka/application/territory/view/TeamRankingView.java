package com.raon.tikitaka.application.territory.view;

import java.util.List;

/** 땅따먹기 랭킹. finalized가 false면 실시간 값이고, true면 땅따먹기 주간 종료 시점의 확정값이다 */
public record TeamRankingView(Long cycleId, boolean finalized, Long myLocationId, List<Team> teams) {

    public record Team(int rank, Long locationId, String locationName, long totalLandPrice,
                       int ownedDongCount, double avgLandPrice) {
    }
}
