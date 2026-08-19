package com.raon.tikitaka.adapter.ranking.out;

import com.raon.tikitaka.application.ranking.SeasonRankRow;
import com.raon.tikitaka.domain.ranking.LocationRanking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LocationRankingJpaRepository extends JpaRepository<LocationRanking, Long> {

    /**
     * 점수 누적 UPSERT — "조회 → 있으면 더하고 없으면 만들기"를 자바에서 하면
     * 동시에 두 게시물이 올라올 때 둘 다 "없음"을 보고 INSERT를 시도해 충돌한다.
     * PostgreSQL의 on conflict가 이 경쟁을 DB 한 번의 원자적 연산으로 해결한다.
     * (uk_location_ranking_stage_location 유니크 제약이 conflict 기준)
     *
     * clearAutomatically: 네이티브 UPDATE는 영속성 컨텍스트를 우회하므로,
     * 실행 후 컨텍스트를 비워 캐시된 낡은 엔티티를 보지 않게 한다.
     * flushAutomatically: 실행 전에 쌓인 변경(게시물 저장 등)을 먼저 DB에 반영한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into location_ranking (stage_id, location_id, location_score, updated_at)
            values (:stageId, :locationId, :delta, :now)
            on conflict (stage_id, location_id)
            do update set location_score = location_ranking.location_score + :delta,
                          updated_at = :now
            """, nativeQuery = true)
    void upsertScore(@Param("stageId") Long stageId,
                     @Param("locationId") Long locationId,
                     @Param("delta") long delta,
                     @Param("now") LocalDateTime now);

    /**
     * 시즌 확정 — 합산·0점 채우기·등수까지 전부 DB가 계산한다.
     * - from location + left join: 점수 행이 없는 미참여 동네도 결과에 남는다
     *   (시즌 조건을 where가 아니라 on에 둔 이유 — where면 미참여 동네가 걸러져 left join이 무력화된다)
     * - coalesce(sum, 0): 미참여 동네의 sum(null)을 0점으로
     * - rank(): 점수 내림차순 등수. 동점 = 같은 등수, 다음 등수는 건너뜀(1-2-2-4)
     * - 별칭(locationId 등)은 SeasonRankRow의 getter와 매칭된다 (인터페이스 프로젝션)
     * - JPQL은 rank() 윈도우 함수를 지원하지 않아 네이티브로 작성
     */
    @Query(value = """
            select l.location_id as locationId,
                   cast(coalesce(sum(lr.location_score), 0) as bigint) as finalScore,
                   cast(rank() over (order by coalesce(sum(lr.location_score), 0) desc) as int) as finalRank
              from location l
              left join location_ranking lr
                     on lr.location_id = l.location_id
                    and lr.stage_id in (select stage_id from stage where season = :season)
             group by l.location_id
             order by finalRank, locationId
            """, nativeQuery = true)
    List<SeasonRankRow> calculateSeasonRanking(@Param("season") int season);
}
