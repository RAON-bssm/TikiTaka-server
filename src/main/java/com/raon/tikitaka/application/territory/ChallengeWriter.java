package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.ChallengeRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryPointPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.UsedPhotoPort;
import com.raon.tikitaka.application.territory.policy.TollPolicy;
import com.raon.tikitaka.domain.enums.ChallengeResult;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.enums.TerritoryEventType;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.territory.Challenge;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryHistory;
import com.raon.tikitaka.domain.territory.UsedPhoto;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.TerritoryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 도전의 돈·판정을 한 트랜잭션으로 처리한다. AI 채점과 S3 업로드는 이미 끝난 뒤에 호출된다.
 * 여기서 예외가 나면 전부 롤백되므로 통행료도 빠져나가지 않는다(시스템 오류 환불이 필요 없는 이유).
 *
 * 1) 땅 행을 FOR UPDATE로 잠근다 → 같은 땅의 동시 도전은 한 줄로 서고, 지금의 대표·방어 점수로 판정된다
 * 2) 조건 재확인(기간, 도전 대상, 최대 보유 수, 사진 재사용)
 * 3) 통행료 차감 + 대표에게 90% 입금. 유저 행은 user_id 순서로 잠가 교착을 피한다
 * 4) 판정 → 점령이면 주인 교체 + 이력
 * 5) 도전 기록·사용한 사진 저장
 */
@Component
@RequiredArgsConstructor
public class ChallengeWriter {

    private final TerritoryQueryPort territoryQueryPort;
    private final TerritoryPointPort pointPort;
    private final ChallengeRepositoryPort challengeRepositoryPort;
    private final UsedPhotoPort usedPhotoPort;
    private final TollPolicy tollPolicy;
    private final TerritoryProperties properties;

    public record Outcome(Challenge challenge, int myPoint) {
    }

    @Transactional
    public Outcome apply(Long territoryId, Users challenger, Location team, String photoKey, String photoHash,
                         Double lat, Double lng, int score, String review) {
        LocalDateTime now = LocalDateTime.now();
        UUID userId = challenger.getUserId();

        Territory t = territoryQueryPort.findByIdForUpdate(territoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "땅을 찾을 수 없습니다."));
        if (t.getCycle().roundTypeAt(now) != RoundType.BATTLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "땅따먹기 주간이 아닙니다.");
        }
        if (!t.isChallengeableBy(userId, team.getLocationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 땅이나 우리 팀 땅에는 도전할 수 없어요.");
        }
        if (territoryQueryPort.countOwnedBy(t.getCycle().getCycleId(), userId) >= properties.maxOwned()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "땅은 최대 " + properties.maxOwned() + "개까지 가질 수 있어요.");
        }
        if (usedPhotoPort.existsByHash(photoHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 사진이에요. 새로 찍어 주세요.");
        }

        int toll = tollPolicy.toll(t.landPrice());
        Users owner = t.getOwner();
        int ownerShare = owner == null ? 0 : tollPolicy.ownerShare(toll);   // 주인 없는 땅은 통행료를 내고 전액 소멸(결정 #15)
        transfer(userId, owner == null ? null : owner.getUserId(), toll, ownerShare);

        int defenseAt = t.getDefenseScore();
        ChallengeResult result = t.isBeatenBy(score) ? ChallengeResult.CAPTURED : ChallengeResult.FAILED;
        if (result == ChallengeResult.CAPTURED) {
            UUID from = owner == null ? null : owner.getUserId();
            t.capture(challenger, team, photoKey, score, now);
            challengeRepositoryPort.saveHistory(TerritoryHistory.of(t.getTerritoryId(), from, userId, score,
                    from == null ? TerritoryEventType.CLAIMED : TerritoryEventType.CAPTURED, now));
        }

        Challenge saved = challengeRepositoryPort.save(Challenge.record(t, challenger, team, photoKey, photoHash,
                lat, lng, score, defenseAt, result, toll, ownerShare, review, now));
        usedPhotoPort.save(UsedPhoto.of(photoHash, userId, now));
        return new Outcome(saved, pointPort.getPoint(userId));
    }

    /**
     * 차감과 입금이 각자 유저 행을 잠근다. A가 B의 땅, B가 A의 땅에 동시에 도전하면
     * 순서가 엇갈려 교착이 날 수 있어서, 항상 user_id가 작은 쪽부터 건드린다.
     * 차감이 실패하면 예외 → 롤백이라 먼저 한 입금도 취소된다.
     */
    private void transfer(UUID challengerId, UUID ownerId, int toll, int ownerShare) {
        boolean ownerFirst = ownerId != null && ownerId.compareTo(challengerId) < 0;
        if (ownerFirst) {
            pointPort.add(ownerId, ownerShare);
        }
        if (!pointPort.deduct(challengerId, toll)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "포인트가 부족해요.");
        }
        if (ownerId != null && !ownerFirst) {
            pointPort.add(ownerId, ownerShare);
        }
    }
}
