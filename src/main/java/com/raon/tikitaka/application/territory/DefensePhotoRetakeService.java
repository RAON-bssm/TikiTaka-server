package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.RetakeDefensePhotoUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.TerritoryScoringPort;
import com.raon.tikitaka.application.territory.out.UsedPhotoPort;
import com.raon.tikitaka.application.territory.policy.PhotoHasher;
import com.raon.tikitaka.application.territory.view.SetupResultView;
import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 대표 사진 재촬영. 땅 세팅과 같은 순서로 싼 검사 → AI 채점 → S3 → 짧은 저장 트랜잭션.
 * 대기 미션이 있으면 그 미션으로 채점하고, 저장할 때 대기 미션이 지금 미션이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefensePhotoRetakeService implements RetakeDefensePhotoUseCase {

    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final UsedPhotoPort usedPhotoPort;
    private final TerritoryScoringPort scoringPort;
    private final StorageUseCase storageUseCase;
    private final DefensePhotoRetakeWriter writer;

    @Override
    public SetupResultView retake(UUID userId, Long dongCode, Command c) {
        LocalDateTime now = LocalDateTime.now();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now)
                .filter(cy -> cy.roundTypeAt(now) == RoundType.BATTLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "대표 사진 재촬영은 땅따먹기 라운드에만 할 수 있어요."));
        Territory t = territoryQueryPort.findByCycleIdAndDongCode(cycle.getCycleId(), dongCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 없습니다."));
        if (!t.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 땅의 대표 사진만 다시 찍을 수 있어요.");
        }
        if (t.getMissionAdjective() == null && !t.hasPendingMission()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "아직 미션이 없는 땅이에요. 잠시 후 다시 시도해 주세요.");
        }

        String hash = PhotoHasher.sha256(c.image());
        if (usedPhotoPort.existsByHash(hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 사진이에요. 새로 찍어 주세요.");
        }

        // 채점할 미션: 대기 미션이 있으면 그것, 없으면 지금 미션
        boolean pending = t.hasPendingMission();
        String adjective = pending ? t.getPendingAdjective() : t.getMissionAdjective();
        String noun = pending ? t.getPendingNoun() : t.getMissionNoun();
        String criteria = pending ? t.getPendingCriteria() : t.getScoringCriteria();

        TerritoryScoringPort.Result result = scoringPort.score(adjective, noun, criteria, c.image(), c.contentType());
        if (result.criteriaViolation()) {
            log.warn("채점 기준 위반으로 재촬영 거절. userId={}, dongCode={}, reason={}", userId, dongCode, result.violationReason());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "채점 기준을 사용할 수 없어요. 미션을 다시 바꿔 주세요. " + result.violationReason());
        }
        if (result.score() == null || result.score() < 0 || result.score() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 채점 결과가 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
        }

        String key = storageUseCase.uploadImage(c.image(), c.fileName(), c.contentType());
        Territory saved = writer.apply(t.getTerritoryId(), userId, adjective, noun, criteria, key, hash, result.score());
        return new SetupResultView(dongCode, saved.getMission(), saved.getMissionAdjective(), saved.getMissionNoun(),
                saved.getScoringCriteria(), saved.getDefensePhoto(), saved.getDefenseScore(), result.review());
    }
}
