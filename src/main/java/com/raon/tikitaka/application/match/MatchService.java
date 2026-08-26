package com.raon.tikitaka.application.match;

import com.raon.tikitaka.application.match.in.GetMatchResultsUseCase;
import com.raon.tikitaka.application.match.in.OpenRoundUseCase;
import com.raon.tikitaka.application.match.out.MatchRepositoryPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.ranking.LocationRankRow;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.global.config.SeasonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 매치와 게시판 자동 생성의 진입점.
 * 매치가 아직 없는 임박한 라운드를 찾아 라운드마다 OpenRoundProcessor를 호출한다.
 * 라운드 하나의 생성은 전부 성공 아니면 전부 롤백이어야 하고 한 라운드의 실패가
 * 다른 라운드까지 되돌리면 안 되므로 트랜잭션은 OpenRoundProcessor.open()에 라운드별로 건다.
 * 내부 호출은 스프링 프록시를 타지 않아 별도 빈으로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService implements OpenRoundUseCase, GetMatchResultsUseCase {

    private final StageRepositoryPort stageRepositoryPort;
    private final SeasonProperties seasonProperties;
    private final OpenRoundProcessor openRoundProcessor;
    private final MatchRepositoryPort matchRepositoryPort;
    private final RankingRepositoryPort rankingRepositoryPort;

    @Override
    public void execute() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusDays(seasonProperties.matchOpenAheadDays());

        List<Stage> targets = stageRepositoryPort.findUpcomingWithoutMatch(now, threshold);
        if (targets.isEmpty()) {
            return;     // 할 일 없음
        }

        for (Stage stage : targets) {
            try {
                openRoundProcessor.open(stage.getStageId());
            } catch (Exception e) {
                // 이 라운드의 트랜잭션은 통째로 롤백됐고 다음 실행에서 재시도된다
                log.error("라운드 개장 실패 (stage {}, season {} round {}): {}",
                        stage.getStageId(), stage.getSeason(), stage.getRound(), e.getMessage(), e);
            }
        }
    }

    /**
     * 직전 종료 라운드의 경기 결과 조회. 승패는 두 팀 라운드 점수를 조회 시점에 비교해 정한다.
     * 점수가 삭제 차감으로 사후 변동될 수 있어 승리 팀을 컬럼에 저장하지 않는다.
     * 동점과 부전승은 승패 없이 내려주고 matchType으로 구분한다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MatchResult> getMatchResults() {
        Optional<Stage> lastEnded = stageRepositoryPort.findLatestEnded(LocalDateTime.now());
        if (lastEnded.isEmpty()) {
            return new ArrayList<>();   // 아직 끝난 라운드가 없으면 빈 목록
        }
        Long stageId = lastEnded.get().getStageId();

        List<Match> matches = matchRepositoryPort.findAllByStageIdWithTeams(stageId);

        // 라운드의 동네별 점수를 한 번에 가져와 맵으로 만든다
        List<LocationRankRow> rows = rankingRepositoryPort.findLocationRanking(stageId);
        Map<Long, Long> scoreByLocationId = new HashMap<>();
        for (LocationRankRow row : rows) {
            scoreByLocationId.put(row.getLocationId(), row.getLocationScore());
        }

        List<MatchResult> results = new ArrayList<>();
        for (Match match : matches) {
            String winTeam = null;
            String lostTeam = null;
            if (match.getMatchType() != MatchType.BYE) {
                Long score1 = scoreByLocationId.getOrDefault(match.getTeam1().getLocationId(), 0L);
                Long score2 = scoreByLocationId.getOrDefault(match.getTeam2().getLocationId(), 0L);
                if (score1 > score2) {
                    winTeam = match.getTeam1().getLocationName();
                    lostTeam = match.getTeam2().getLocationName();
                } else if (score2 > score1) {
                    winTeam = match.getTeam2().getLocationName();
                    lostTeam = match.getTeam1().getLocationName();
                }
                // 동점이면 둘 다 null로 둔다
            }
            results.add(new MatchResult(
                    match.getMatchId(),
                    match.getMission(),
                    winTeam,
                    lostTeam,
                    match.getMatchType().getDescription()));
        }
        return results;
    }
}
