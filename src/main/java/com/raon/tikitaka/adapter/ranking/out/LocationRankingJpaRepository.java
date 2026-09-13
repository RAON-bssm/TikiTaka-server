package com.raon.tikitaka.adapter.ranking.out;

import com.raon.tikitaka.application.ranking.LocationRankRow;
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
     * 점수 누적 upsert. 조회 후 갱신을 자바에서 하면 동시 요청이 겹칠 때
     * 둘 다 INSERT를 시도해 충돌하므로 on conflict로 DB에서 원자적으로 처리한다.
     * 네이티브 쿼리는 영속성 컨텍스트를 우회하므로 실행 전 flush, 실행 후 clear를 걸어둔다.
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
     * 시즌 확정용 랭킹 계산. 합산과 0점 채우기, 등수까지 전부 DB가 계산한다.
     * location 기준 left join이라 미참여 동네도 0점으로 남고, 시즌 조건을 where에 두면
     * 미참여 동네가 걸러지므로 on에 둔다. 동점은 rank()가 같은 등수로 처리한다.
     * JPQL이 rank()를 지원하지 않아 네이티브 쿼리로 작성했다.
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

    /**
     * 단일 라운드의 지역 랭킹. 시즌 확정과 같은 계산법이라
     * 진행 중 순위와 확정 순위가 같은 규칙으로 매겨진다.
     */
    @Query(value = """
            select l.location_id as locationId,
                   l.city_name as cityName,
                   l.location_name as locationName,
                   cast(coalesce(sum(lr.location_score), 0) as bigint) as locationScore,
                   cast(rank() over (order by coalesce(sum(lr.location_score), 0) desc) as int) as locationRank
              from location l
              left join location_ranking lr
                     on lr.location_id = l.location_id
                    and lr.stage_id = :stageId
             group by l.location_id, l.city_name, l.location_name
             order by locationRank, locationId
            """, nativeQuery = true)
    List<LocationRankRow> findLocationRanking(@Param("stageId") Long stageId);
}
