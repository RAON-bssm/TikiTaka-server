package com.raon.tikitaka.application.match;

import com.raon.tikitaka.application.board.out.BoardRepositoryPort;
import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.match.out.KeywordRepositoryPort;
import com.raon.tikitaka.application.match.out.MatchRepositoryPort;
import com.raon.tikitaka.application.match.out.MissionGeneratorPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.MatchType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * 라운드 하나의 매치·게시판을 생성한다 — 반드시 단일 트랜잭션.
 * 중간에 실패하면 전부 롤백되어 "매치가 하나도 없는" 상태로 돌아가고,
 * 다음 실행의 멱등성 체크(existsByStage)가 정상 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRoundProcessor {

    private static final String TYPE_NOUN = "명사";
    private static final String TYPE_ADJECTIVE = "형용사";
    private static final int MAX_SHUFFLE_ATTEMPTS = 10;

    private final StageRepositoryPort stageRepositoryPort;
    private final MatchRepositoryPort matchRepositoryPort;
    private final BoardRepositoryPort boardRepositoryPort;
    private final LocationRepositoryPort locationRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final KeywordRepositoryPort keywordRepositoryPort;
    private final MissionGeneratorPort missionGeneratorPort;

    private final Random random = new Random();

    @Transactional
    public void open(Long stageId) {
        Optional<Stage> stageOptional = stageRepositoryPort.findById(stageId);
        if (stageOptional.isEmpty()) {
            throw new IllegalStateException("존재하지 않는 라운드입니다: " + stageId);
        }
        Stage stage = stageOptional.get();

        // 멱등성 2차 방어 — 목록 조회와 처리 사이에 다른 실행이 끼어든 경우
        if (matchRepositoryPort.existsByStage(stage)) {
            return;
        }

        // 1) 동네별 "예정 소속" ACTIVE 인원 집계 (스위칭 예약자는 sub 지역으로 카운트)
        //    쿼리는 유저 1명당 소속 동네 ID를 1건씩 돌려주고, 여기서 동네별로 센다
        Map<Long, Integer> memberCounts = new HashMap<>();
        List<Long> expectedLocationIds = userRepositoryPort.findExpectedLocationIdsOfActiveUsers();
        for (Long locationId : expectedLocationIds) {
            Integer currentCount = memberCounts.get(locationId);
            if (currentCount == null) {
                memberCounts.put(locationId, 1);
            } else {
                memberCounts.put(locationId, currentCount + 1);
            }
        }

        // 2) C = 전체 ACTIVE 유저 수 / 전체 동네 수 → Stage에 스냅샷 (double로 끝까지, 중간 반올림 금지)
        List<Location> allLocations = locationRepositoryPort.findAll();
        if (allLocations.isEmpty()) {
            log.warn("location 테이블이 비어 있어 매치를 만들 수 없습니다 (stage {})", stageId);
            return;
        }
        // 쿼리가 유저 1명당 1건을 돌려주므로 명단 길이가 곧 전체 ACTIVE 유저 수다
        int totalActiveUsers = expectedLocationIds.size();
        double avgMemberCount = (double) totalActiveUsers / allLocations.size();
        stage.assignAvgLocationMemberCount(avgMemberCount);

        // 3) 참가 동네 = ACTIVE 인원 1명 이상 (0명 동네는 매칭 제외 — 확정 내역 2번)
        List<Location> participants = new ArrayList<>();
        for (Location location : allLocations) {
            Integer count = memberCounts.get(location.getLocationId());
            if (count != null && count > 0) {
                participants.add(location);
            }
        }
        if (participants.isEmpty()) {
            log.warn("ACTIVE 주민이 있는 동네가 없어 매치를 만들지 않습니다 (stage {})", stageId);
            return;
        }

        // 4) 미션은 매치마다 각각 생성한다 (확정 — 매치별 상이).
        //    같은 라운드 안에서 같은 키워드 조합이 반복되지 않도록 사용한 조합을 기록한다
        Set<String> usedKeywordCombos = new HashSet<>();

        Set<Long> assigned = new HashSet<>();
        int matchCount = 0;

        // 5) 홀수면 BYE 이력이 가장 오래된 동네를 부전승(셀프 미션 매치)으로 (확정 내역 4번)
        if (participants.size() % 2 == 1) {
            Location bye = selectByeLocation(participants);
            participants.remove(bye);
            validateAndMark(assigned, bye);
            int byeMemberCount = memberCounts.get(bye.getLocationId());
            String byeMission = createMission(usedKeywordCombos);
            createMatchWithBoard(stage, bye, bye, MatchType.BYE, byeMemberCount, byeMemberCount, byeMission);
            matchCount++;
            log.info("부전승(BYE) 매치 생성: {} (stage {})", bye.getLocationName(), stageId);
        }

        // 6) 랜덤 짝짓기 + 직전 라운드 상대 회피 (확정 내역 2번)
        //    무작위로 섞은 뒤 인접한 동네끼리 짝짓는다. 직전 라운드에서 붙었던 짝이 나오면
        //    다시 섞는다(최대 10회). 동네가 적어 회피가 불가능하면 리매치를 허용한다.
        Map<Long, Long> lastOpponents = findLastOpponents(stage);
        for (int attempt = 1; attempt <= MAX_SHUFFLE_ATTEMPTS; attempt++) {
            Collections.shuffle(participants, random);
            if (!hasRematch(participants, lastOpponents)) {
                break;
            }
            if (attempt == MAX_SHUFFLE_ATTEMPTS) {
                log.warn("직전 라운드 상대 회피에 실패해 리매치를 허용합니다 (stage {})", stageId);
            }
        }

        for (int i = 0; i + 1 < participants.size(); i += 2) {
            Location team1 = participants.get(i);
            Location team2 = participants.get(i + 1);
            validateAndMark(assigned, team1);
            validateAndMark(assigned, team2);
            String mission = createMission(usedKeywordCombos);
            createMatchWithBoard(stage, team1, team2, MatchType.NORMAL,
                    memberCounts.get(team1.getLocationId()),
                    memberCounts.get(team2.getLocationId()),
                    mission);
            matchCount++;
        }

        log.info("라운드 개장 완료: stage {} (season {} round {}) — 매치 {}개, C = {}",
                stageId, stage.getSeason(), stage.getRound(), matchCount, avgMemberCount);
    }

    /**
     * 키워드(형용사+명사)를 무작위로 뽑아 미션 문장을 생성한다 — 매치마다 호출된다.
     * 같은 라운드에서 이미 쓴 조합은 피한다(최대 10회 재추첨, 조합이 바닥나면 중복 허용).
     */
    private String createMission(Set<String> usedKeywordCombos) {
        List<String> adjectives = keywordRepositoryPort.findKeywordsByType(TYPE_ADJECTIVE);
        List<String> nouns = keywordRepositoryPort.findKeywordsByType(TYPE_NOUN);
        if (adjectives.isEmpty() || nouns.isEmpty()) {
            throw new IllegalStateException(
                    "keyword 테이블에 명사/형용사 시드가 없어 미션을 만들 수 없습니다. (명사 "
                            + nouns.size() + "개, 형용사 " + adjectives.size() + "개)");
        }

        String adjective = null;
        String noun = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidateAdjective = adjectives.get(random.nextInt(adjectives.size()));
            String candidateNoun = nouns.get(random.nextInt(nouns.size()));
            String combo = candidateAdjective + "|" + candidateNoun;
            if (!usedKeywordCombos.contains(combo)) {
                usedKeywordCombos.add(combo);
                adjective = candidateAdjective;
                noun = candidateNoun;
                break;
            }
        }
        if (adjective == null) {
            // 10번 다 이미 쓴 조합 — 키워드가 극히 적은 상황이니 중복을 허용한다
            adjective = adjectives.get(random.nextInt(adjectives.size()));
            noun = nouns.get(random.nextInt(nouns.size()));
        }
        return missionGeneratorPort.generate(adjective, noun);
    }

    /**
     * 직전 라운드의 "동네 → 상대 동네" 지도.
     * 라운드는 빈틈없이 이어지므로 직전 라운드 = ended_at이 이번 라운드의 started_at과 같은 라운드다.
     * BYE(셀프) 매치는 상대가 아니므로 제외한다.
     */
    private Map<Long, Long> findLastOpponents(Stage stage) {
        Map<Long, Long> lastOpponents = new HashMap<>();
        List<Match> previousMatches = matchRepositoryPort.findAllByStageEndedAt(stage.getStartedAt());
        for (Match previous : previousMatches) {
            Long team1Id = previous.getTeam1().getLocationId();
            Long team2Id = previous.getTeam2().getLocationId();
            if (!team1Id.equals(team2Id)) {
                lastOpponents.put(team1Id, team2Id);
                lastOpponents.put(team2Id, team1Id);
            }
        }
        return lastOpponents;
    }

    /**
     * 현재 순서로 인접 짝짓기를 했을 때, 직전 라운드에서 붙었던 짝이 하나라도 있으면 true.
     */
    private boolean hasRematch(List<Location> shuffled, Map<Long, Long> lastOpponents) {
        for (int i = 0; i + 1 < shuffled.size(); i += 2) {
            Long team1Id = shuffled.get(i).getLocationId();
            Long team2Id = shuffled.get(i + 1).getLocationId();
            Long lastOpponentOfTeam1 = lastOpponents.get(team1Id);
            if (lastOpponentOfTeam1 != null && lastOpponentOfTeam1.equals(team2Id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * BYE 이력이 없는 동네 → 가장 오래전에 BYE였던 동네 순. 동률은 location_id 순 (확정 내역 4번).
     * 이력은 match_type = BYE 매치의 시작 시각으로 판단하므로 별도 컬럼이 필요 없다.
     */
    private Location selectByeLocation(List<Location> participants) {
        // 동네별 "마지막으로 부전승이었던 라운드의 시작 시각"을 모은다
        Map<Long, LocalDateTime> lastByeAt = new HashMap<>();
        List<Match> byeMatches = matchRepositoryPort.findAllByMatchType(MatchType.BYE);
        for (Match byeMatch : byeMatches) {
            Long locationId = byeMatch.getTeam1().getLocationId();
            LocalDateTime startedAt = byeMatch.getStage().getStartedAt();
            LocalDateTime existing = lastByeAt.get(locationId);
            if (existing == null || startedAt.isAfter(existing)) {
                lastByeAt.put(locationId, startedAt);
            }
        }

        // 이력 시각이 가장 오래된(없으면 최우선) 동네를 고른다. 동률이면 location_id가 작은 쪽
        Location selected = null;
        LocalDateTime selectedLastByeAt = null;
        for (Location candidate : participants) {
            LocalDateTime candidateLastByeAt = lastByeAt.get(candidate.getLocationId());
            if (candidateLastByeAt == null) {
                candidateLastByeAt = LocalDateTime.MIN;     // 이력 없음 = 가장 오래된 것으로 취급
            }

            if (selected == null) {
                selected = candidate;
                selectedLastByeAt = candidateLastByeAt;
                continue;
            }
            if (candidateLastByeAt.isBefore(selectedLastByeAt)) {
                selected = candidate;
                selectedLastByeAt = candidateLastByeAt;
                continue;
            }
            if (candidateLastByeAt.isEqual(selectedLastByeAt)
                    && candidate.getLocationId() < selected.getLocationId()) {
                selected = candidate;
                selectedLastByeAt = candidateLastByeAt;
            }
        }
        return selected;
    }

    private void createMatchWithBoard(Stage stage, Location team1, Location team2, MatchType type,
                                      int team1Count, int team2Count, String mission) {
        Match match = matchRepositoryPort.save(
                Match.create(stage, team1, team2, type, team1Count, team2Count, mission));
        boardRepositoryPort.save(Board.create(match));
    }

    private void validateAndMark(Set<Long> assigned, Location location) {
        if (!assigned.add(location.getLocationId())) {
            throw new IllegalStateException(
                    "동네가 한 라운드에 두 경기에 배정되려 합니다: " + location.getLocationId());
        }
    }
}
