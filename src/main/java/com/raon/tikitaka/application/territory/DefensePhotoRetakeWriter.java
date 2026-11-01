package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.UsedPhotoPort;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.UsedPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 재촬영의 DB 반영. 채점하는 사이에 땅을 빼앗겼거나, 라운드가 끝났거나, 미션이 또 바뀌었을 수 있어
 * 땅 행을 잠그고 다시 확인한 뒤 저장한다. 별도 빈이어야 @Transactional 프록시가 적용된다.
 */
@Component
@RequiredArgsConstructor
public class DefensePhotoRetakeWriter {

    private final TerritoryQueryPort territoryQueryPort;
    private final UsedPhotoPort usedPhotoPort;

    @Transactional
    public Territory apply(Long territoryId, UUID userId, String scoredAdjective, String scoredNoun,
                           String scoredCriteria, String photoKey, String photoHash, int score) {
        LocalDateTime now = LocalDateTime.now();
        Territory t = territoryQueryPort.findByIdForUpdate(territoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "땅을 찾을 수 없습니다."));
        if (t.getCycle().roundTypeAt(now) != RoundType.BATTLE || !t.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "땅따먹기 라운드가 아니거나 더 이상 내 땅이 아니에요.");
        }
        // 채점에 쓴 미션과 지금 적용될 미션이 다르면(그 사이 미션을 또 바꿈) 저장하지 않는다
        boolean pending = t.hasPendingMission();
        String adjective = pending ? t.getPendingAdjective() : t.getMissionAdjective();
        String noun = pending ? t.getPendingNoun() : t.getMissionNoun();
        String criteria = pending ? t.getPendingCriteria() : t.getScoringCriteria();
        if (!scoredAdjective.equals(adjective) || !scoredNoun.equals(noun) || !Objects.equals(scoredCriteria, criteria)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "채점하는 사이 미션이 바뀌었어요. 다시 찍어 주세요.");
        }
        if (usedPhotoPort.existsByHash(photoHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 사진이에요. 새로 찍어 주세요.");
        }
        t.retake(photoKey, score, now);
        usedPhotoPort.save(UsedPhoto.of(photoHash, userId, now));
        return t;
    }
}
