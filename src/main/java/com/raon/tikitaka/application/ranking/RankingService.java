package com.raon.tikitaka.application.ranking;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.ranking.in.FinalizeSeasonUseCase;
import com.raon.tikitaka.application.ranking.in.GetLocationRankingUseCase;
import com.raon.tikitaka.application.ranking.in.GetUserRankingUseCase;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.ranking.SeasonLocationResult;
import com.raon.tikitaka.global.config.SeasonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RankingService implements FinalizeSeasonUseCase, GetLocationRankingUseCase, GetUserRankingUseCase {

    /**
     * 개인 랭킹 목록 상한. 유저가 늘어도 응답이 무한정 커지지 않게 자른다.
     * 본인 순위는 별도 쿼리로 항상 내려주므로 100등 밖 유저도 자기 위치를 볼 수 있다.
     */
    private static final int USER_RANKING_LIMIT = 100;

    private final StageRepositoryPort stageRepositoryPort;
    private final RankingRepositoryPort rankingRepositoryPort;
    private final SeasonResultRepositoryPort seasonResultRepositoryPort;
    private final LocationRepositoryPort locationRepositoryPort;
    private final SeasonProperties seasonProperties;

    /**
     * 라운드가 roundsPerSeason 개수만큼 존재하고 마지막 라운드까지 전부 종료됐으며
     * 아직 확정 결과가 없는 시즌만 확정한다. 이미 확정된 시즌은 다시 계산하지 않는다.
     */
    @Override
    public void execute() {
        List<Stage> stages = stageRepositoryPort.findAll();
        LocalDateTime now = LocalDateTime.now();

        // 시즌별 라운드 수와 가장 늦은 종료 시각을 집계한다. TreeMap이라 시즌 오름차순으로 돈다
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
     * 시즌 하나를 확정한다. 합산과 0점 채우기, 등수 계산은 전부 DB가 하고
     * 여기서는 결과를 받아 저장만 한다.
     * 쿼리 상세는 LocationRankingJpaRepository.calculateSeasonRanking 참고.
     */
    private void finalizeSeason(int season) {
        List<SeasonRankRow> rows = rankingRepositoryPort.calculateSeasonRanking(season);

        // 저장할 때 Location 엔티티가 필요하므로 ID로 찾을 수 있게 맵을 만들어둔다
        List<Location> locations = locationRepositoryPort.findAll();
        Map<Long, Location> locationById = new HashMap<>();
        for (Location location : locations) {
            locationById.put(location.getLocationId(), location);
        }

        for (SeasonRankRow row : rows) {
            Location location = locationById.get(row.getLocationId());
            if (location == null) {
                // 쿼리가 location 테이블 기준이라 이론상 불가능. 생기면 데이터 이상이므로 바로 예외를 던진다
                throw new IllegalStateException("확정 결과의 동네를 찾을 수 없습니다. locationId=" + row.getLocationId());
            }
            seasonResultRepositoryPort.save(SeasonLocationResult.create(
                    season, location, row.getFinalRank(), row.getFinalScore()));
        }

        log.info("시즌 {} 확정 완료. 동네 {}곳 기록", season, rows.size());
    }

    @Override
    public List<LocationRankRow> getLocationRanking() {
        Stage current = currentStage();
        return rankingRepositoryPort.findLocationRanking(current.getStageId());
    }

    @Override
    public UserRankingResult getUserRanking(UUID userId) {
        Stage current = currentStage();
        List<UserRankRow> ranking = rankingRepositoryPort.findTopUserRanking(current.getStageId(), USER_RANKING_LIMIT);

        Optional<UserRankRow> mine = rankingRepositoryPort.findMyUserRanking(current.getStageId(), userId);
        UserRankRow myRanking = null;   // 이번 라운드 미참여면 null이고 응답에서 필드가 생략된다
        if (mine.isPresent()) {
            myRanking = mine.get();
        }
        return new UserRankingResult(ranking, myRanking);
    }

    private Stage currentStage() {
        Optional<Stage> current = stageRepositoryPort.findCurrent(LocalDateTime.now());
        if (current.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "진행 중인 라운드가 없습니다.");
        }
        return current.get();
    }
}
