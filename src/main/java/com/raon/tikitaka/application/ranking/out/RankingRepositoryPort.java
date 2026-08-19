package com.raon.tikitaka.application.ranking.out;

import com.raon.tikitaka.application.ranking.SeasonRankRow;

import java.util.List;
import java.util.UUID;

public interface RankingRepositoryPort {

    /**
     * (stage, location) 지역 점수에 delta를 누적한다 — 행이 없으면 만들고, 있으면 더한다(UPSERT).
     * delta가 음수면 차감(게시물 삭제)이다.
     */
    void addLocationScore(Long stageId, Long locationId, long delta);

    /**
     * (stage, user) 개인 점수에 delta를 누적한다 — 행이 없으면 만들고, 있으면 더한다(UPSERT).
     */
    void addUserScore(Long stageId, UUID userId, long delta);

    /**
     * 시즌 확정용 — 전체 동네의 시즌 합산 점수와 최종 등수를 DB가 계산해 등수순으로 돌려준다.
     * 미참여 동네도 0점으로 포함되고, 동점은 같은 등수(1-2-2-4 방식)다.
     */
    List<SeasonRankRow> calculateSeasonRanking(int season);
}
