package com.raon.tikitaka.adapter.match.out;

import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchJpaRepository extends JpaRepository<Match, Long> {

    boolean existsByStage(Stage stage);

    @Query("""
            select m from Match m
            join fetch m.stage
            join fetch m.team1
            where m.matchType = :matchType
            """)
    List<Match> findAllByMatchType(@Param("matchType") MatchType matchType);

    @Query("""
            select m from Match m
            join fetch m.team1
            join fetch m.team2
            where m.stage.endedAt = :endedAt
            """)
    List<Match> findAllByStageEndedAt(@Param("endedAt") LocalDateTime endedAt);
}
