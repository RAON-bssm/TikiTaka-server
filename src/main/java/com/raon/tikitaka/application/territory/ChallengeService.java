package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.ChallengeUseCase;
import com.raon.tikitaka.application.territory.out.*;
import com.raon.tikitaka.application.territory.policy.PhotoHasher;
import com.raon.tikitaka.application.territory.policy.TollPolicy;
import com.raon.tikitaka.application.territory.view.ChallengeResultView;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.Challenge;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import com.raon.tikitaka.global.guard.RequestGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 도전 흐름. 돈이 걸린 판정은 ChallengeWriter의 한 트랜잭션에서만 한다.
 * 여기서의 사전 확인은 AI·S3 비용을 쓰기 전에 거르려는 것이고, Writer가 같은 조건을 다시 확인한다.
 * 사전 확인 → AI 채점 → S3 업로드 → [포인트 키] 트랜잭션(차감·입금·판정·기록)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService implements ChallengeUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final TerritoryPointPort pointPort;
    private final UsedPhotoPort usedPhotoPort;
    private final TerritoryScoringPort scoringPort;
    private final StorageUseCase storageUseCase;
    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final ChallengeWriter writer;
    private final TollPolicy tollPolicy;
    private final TerritoryProperties properties;
    private final RequestGuard requestGuard;

    @Override
    public ChallengeResultView challenge(UUID userId, Long dongCode, Command c) {
        LocalDateTime now = LocalDateTime.now();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now)
                .filter(cy -> cy.roundTypeAt(now) == RoundType.BATTLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "땅따먹기 주간이 아닙니다."));
        Territory t = territoryQueryPort.findByCycleIdAndDongCode(cycle.getCycleId(), dongCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 없습니다."));
        Users user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));

        // ---- 사전 확인 (비용 0) ----
        if (!t.isChallengeableBy(userId, user.getMainLocation().getLocationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 땅이나 우리 팀 땅에는 도전할 수 없어요.");
        }
        if (pointPort.getPoint(userId) < tollPolicy.toll(t.landPrice())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "포인트가 부족해요.");
        }
        if (territoryQueryPort.countOwnedBy(cycle.getCycleId(), userId) >= properties.maxOwned()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "땅은 최대 " + properties.maxOwned() + "개까지 가질 수 있어요.");
        }
        String hash = PhotoHasher.sha256(c.image());
        if (usedPhotoPort.existsByHash(hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 사진이에요. 새로 찍어 주세요.");
        }

        // ---- AI 채점 (그 동의 미션·채점 기준으로) ----
        TerritoryScoringPort.Result r = scoringPort.score(t.getMissionAdjective(), t.getMissionNoun(),
                t.getScoringCriteria(), c.image(), c.contentType());
        if (r.criteriaViolation()) {
            // 채점 기준은 세팅 때 이미 통과했다. 여기서 위반이 나오면 모델 판단 흔들림이므로 도전자 탓으로 0점 처리하지 않는다
            log.warn("도전 채점 중 채점 기준 위반 판정. territoryId={}, reason={}", t.getTerritoryId(), r.violationReason());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "채점에 실패했어요. 잠시 후 다시 시도해 주세요.");
        }
        if (r.score() == null || r.score() < 0 || r.score() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 채점 결과가 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
        }

        String key = storageUseCase.uploadImage(c.image(), c.fileName(), c.contentType());

        // ---- 돈·판정 (한 트랜잭션). 상점·뽑기와 같은 포인트 키로 줄을 세운다 ----
        ChallengeWriter.Outcome o = requestGuard.executeWaiting(RequestGuard.pointsKey(userId),
                RequestGuard.POINTS_BUSY_MESSAGE,
                () -> writer.apply(t.getTerritoryId(), user, user.getMainLocation(), key, hash,
                        c.lat(), c.lng(), r.score(), r.review()));
        return toView(o.challenge(), t, o.myPoint());
    }

    @Override
    public ChallengeResultView getResult(UUID userId, UUID challengeId) {
        Challenge ch = challengeRepositoryPort.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "도전 기록이 없습니다."));
        if (!ch.getChallenger().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 도전만 볼 수 있어요.");
        }
        return toView(ch, ch.getTerritory(), null);
    }

    private static ChallengeResultView toView(Challenge ch, Territory t, Integer myPoint) {
        return new ChallengeResultView(ch.getChallengeId(), t.getDong().getDongCode(), t.getDong().getDongName(),
                t.getMission(), ch.getResult(), ch.getScore(), ch.getDefenseScoreAt(), ch.getToll(),
                ch.getAiReview(), ch.getCreatedAt(), myPoint);
    }
}
