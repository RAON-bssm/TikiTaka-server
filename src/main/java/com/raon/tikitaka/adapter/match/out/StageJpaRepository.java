package com.raon.tikitaka.adapter.match.out;

import com.raon.tikitaka.domain.match.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StageJpaRepository extends JpaRepository<Stage, Long> {

    Optional<Stage> findTopByOrderByEndedAtDesc();

    /**
     * 매치가 아직 없는 임박 라운드와 진행 중 라운드를 조회한다.
     * now < endedAt 조건이 없으면 매치 없이 지나간 과거 라운드까지 전부 걸린다.
     */
    @Query("""
            select s from Stage s
            where s.startedAt <= :threshold
              and :now < s.endedAt
              and not exists (select 1 from Match m where m.stage = s)
            order by s.startedAt
            """)
    List<Stage> findUpcomingWithoutMatch(@Param("now") LocalDateTime now,
                                         @Param("threshold") LocalDateTime threshold);

    /**
     * 지금 진행 중인 라운드. 경계가 started_at <= now < ended_at 이라
     * 라운드가 갈리는 자정에도 정확히 한 라운드만 걸린다.
     */
    @Query("""
            select s from Stage s
            where s.startedAt <= :now
              and :now < s.endedAt
            """)
    Optional<Stage> findCurrent(@Param("now") LocalDateTime now);

    /**
     * 가장 최근에 종료된 라운드. 종료 시각이 now 이전인 것 중 제일 늦은 라운드다.
     */
    Optional<Stage> findTopByEndedAtLessThanEqualOrderByEndedAtDesc(LocalDateTime now);
}
