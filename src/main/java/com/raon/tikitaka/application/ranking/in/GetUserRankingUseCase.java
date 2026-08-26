package com.raon.tikitaka.application.ranking.in;

import com.raon.tikitaka.application.ranking.UserRankingResult;

import java.util.UUID;

public interface GetUserRankingUseCase {

    /**
     * 현재 진행 중인 라운드의 개인 랭킹 상위 100명과 호출자 본인의 순위.
     */
    UserRankingResult getUserRanking(UUID userId);
}
