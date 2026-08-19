package com.raon.tikitaka.application.ranking;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.ranking.in.FinalizeSeasonUseCase;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.ranking.SeasonLocationResult;
import com.raon.tikitaka.global.config.SeasonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RankingService implements FinalizeSeasonUseCase {

    private final StageRepositoryPort stageRepositoryPort;
    private final RankingRepositoryPort rankingRepositoryPort;
    private final SeasonResultRepositoryPort seasonResultRepositoryPort;
    private final LocationRepositoryPort locationRepositoryPort;
    private final SeasonProperties seasonProperties;

    /**
     * 확정 조건 3가지를 모두 만족하는 시즌만 확정한다:
     * 1) 라운드가 정확히 roundsPerSeason(4)개 존재 — 덜 만들어진 시즌 방지
     * 2) 마지막 라운드까지 전부 종료(ended_at <= now) — 진행 중 시즌 방지
     * 3) season_location_result에 아직 없음 — 재확정 방지 (확정 정책 ⑥: 번복 없음)
     */
    @Override
    public void execute() {
        List<Stage> stages = stageRepositoryPort.findAll();
        LocalDateTime now = LocalDateTime.now();

        // 시즌별 라운드 수와 가장 늦은 종료 시각을 집계한다 (TreeMap → 시즌 오름차순 처리)
        Map<Integer, Integer> roundCounts = new TreeMap<>();
        Map<Integer, LocalDateTime> latestEndedAt = new HashMap<>();
        for (Stage stage : stages) {
            Integer season = stage.getSeason();
            Integer count = roundCounts.get(season);
            if (count == null) {
                roundCounts.put(season, 1);
            } else {
                roundCounts.put(season, count + 1);
            }
            LocalDateTime latest = latestEndedAt.get(season);
            if (latest == null || stage.getEndedAt().isAfter(latest)) {
                latestEndedAt.put(season, stage.getEndedAt());
            }
        }

        for (Map.Entry<Integer, Integer> entry : roundCounts.entrySet()) {
            int season = entry.getKey();
            int roundCount = entry.getValue();
            if (roundCount != seasonProperties.roundsPerSeason()) {
                continue;
            }
            if (latestEndedAt.get(season).isAfter(now)) {
                continue;   // 아직 진행 중인 시즌
            }
            if (seasonResultRepositoryPort.existsBySeason(season)) {
                continue;   // 이미 확정된 시즌
            }
            finalizeSeason(season);
        }
    }

    /**
     * 시즌 하나를 확정한다 — 합산·0점 채우기·동점 처리(1-2-2-4)·등수는
     * 전부 DB(rank() 윈도우 함수)가 계산하고, 여기서는 결과를 받아 박제만 한다.
     * 쿼리 상세는 LocationRankingJpaRepository.calculateSeasonRanking 참고.
     */
    private void finalizeSeason(int season) {
        List<SeasonRankRow> rows = rankingRepositoryPort.calculateSeasonRanking(season);

        // 저장할 때 Location 엔티티(FK)가 필요하므로 ID → 엔티티 맵을 만들어둔다
        List<Location> locations = locationRepositoryPort.findAll();
        Map<Long, Location> locationById = new HashMap<>();
        for (Location location : locations) {
            locationById.put(location.getLocationId(), location);
        }

        for (SeasonRankRow row : rows) {
            Location location = locationById.get(row.getLocationId());
            if (location == null) {
                // 쿼리가 location 테이블 기준이라 이론상 불가능 — 생기면 데이터 이상이므로 조용히 넘기지 않는다
                throw new IllegalStateException("확정 결과의 동네를 찾을 수 없습니다. locationId=" + row.getLocationId());
            }
            seasonResultRepositoryPort.save(SeasonLocationResult.create(
                    season, location, row.getFinalRank(), row.getFinalScore()));
        }

        log.info("시즌 {} 확정 완료 — 동네 {}곳 기록", season, rows.size());
    }
}
