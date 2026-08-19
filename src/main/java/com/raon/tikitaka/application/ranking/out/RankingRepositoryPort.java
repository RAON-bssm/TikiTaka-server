package com.raon.tikitaka.application.ranking.out;

import com.raon.tikitaka.application.ranking.LocationRankRow;
import com.raon.tikitaka.application.ranking.SeasonRankRow;
import com.raon.tikitaka.application.ranking.UserRankRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RankingRepositoryPort {

    /**
     * 라운드별 지역 점수에 delta를 누적한다. 행이 없으면 만들고 있으면 더한다.
     * delta가 음수면 게시물 삭제에 따른 차감이다.
     */
    void addLocationScore(Long stageId, Long locationId, long delta);

    /**
     * 라운드별 개인 점수에 delta를 누적한다. 행이 없으면 만들고 있으면 더한다.
     */
    void addUserScore(Long stageId, UUID userId, long delta);

    /**
     * 시즌 확정에 쓴다. 전체 동네의 시즌 합산 점수와 최종 등수를 DB가 계산해 등수순으로 돌려준다.
     * 미참여 동네도 0점으로 포함되고 동점은 같은 등수다.
     */
    List<SeasonRankRow> calculateSeasonRanking(int season);

    /**
     * 단일 라운드의 지역 랭킹. 미참여 동네도 0점으로 포함해 등수순으로 돌려준다.
     * 랭킹 조회 API와 경기 결과의 승패 계산이 함께 쓴다.
     */
    List<LocationRankRow> findLocationRanking(Long stageId);

    /**
     * 라운드 개인 랭킹 상위 limit명. 게시물을 올린 유저만 포함된다.
     */
    List<UserRankRow> findTopUserRanking(Long stageId, int limit);

    /**
     * 라운드에서 본인의 순위. 미참여면 비어 있다.
     */
    Optional<UserRankRow> findMyUserRanking(Long stageId, UUID userId);
}
