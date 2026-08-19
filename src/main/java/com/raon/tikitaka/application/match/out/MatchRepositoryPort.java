package com.raon.tikitaka.application.match.out;

import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchRepositoryPort {

    Match save(Match match);

    boolean existsByStage(Stage stage);

    /**
     * BYE 로테이션용 — 과거 BYE 매치 전체 (stage 포함 로딩).
     * 동네별 마지막 부전승 시점을 계산해 "가장 오래된 동네부터" 부전승을 배정한다.
     */
    List<Match> findAllByMatchType(MatchType matchType);

    /**
     * 리매치 회피용 — 직전 라운드의 매치 목록.
     * 라운드는 빈틈없이 이어지므로 "ended_at = 이번 라운드의 started_at"인 라운드가 직전 라운드다.
     */
    List<Match> findAllByStageEndedAt(LocalDateTime endedAt);
}
