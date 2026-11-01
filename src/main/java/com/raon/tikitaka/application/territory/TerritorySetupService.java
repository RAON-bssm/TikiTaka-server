package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.storage.in.StorageUseCase;
import com.raon.tikitaka.application.territory.in.SetupTerritoryUseCase;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.TerritoryScoringPort;
import com.raon.tikitaka.application.territory.out.UsedPhotoPort;
import com.raon.tikitaka.application.territory.policy.MissionTextPolicy;
import com.raon.tikitaka.application.territory.policy.PhotoHasher;
import com.raon.tikitaka.application.territory.view.SetupResultView;
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
 * 땅 세팅 흐름. 싼 검사부터 하고 비싼 호출은 마지막에 한다.
 * 입력 규칙·금칙어(서버) → 기간·내 땅(DB 읽기) → 사진 재사용(DB 읽기) → AI 채점 → S3 업로드 → 저장(짧은 트랜잭션)
 * AI 채점을 S3 업로드보다 먼저 해서, 채점 기준 위반으로 거절될 때 업로드 비용을 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerritorySetupService implements SetupTerritoryUseCase {

    private final MissionTextPolicy missionTextPolicy;
    private final TerritoryCycleRepositoryPort cycleRepositoryPort;
    private final TerritoryQueryPort territoryQueryPort;
    private final UsedPhotoPort usedPhotoPort;
    private final TerritoryScoringPort scoringPort;
    private final StorageUseCase storageUseCase;
    private final TerritorySetupWriter writer;

    @Override
    public SetupResultView setup(UUID userId, Long dongCode, Command c) {
        String criteria = c.scoringCriteria() == null || c.scoringCriteria().isBlank() ? null : c.scoringCriteria().trim();
        missionTextPolicy.validate(c.adjective(), c.noun(), criteria);

        LocalDateTime now = LocalDateTime.now();
        TerritoryCycle cycle = cycleRepositoryPort.findCurrent(now)
                .filter(cy -> cy.isSetupPeriod(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "땅 세팅 기간이 아닙니다."));
        Territory territory = territoryQueryPort.findByCycleIdAndDongCode(cycle.getCycleId(), dongCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "이 동의 땅이 없습니다."));
        if (!territory.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 땅만 세팅할 수 있어요.");
        }

        String hash = PhotoHasher.sha256(c.image());
        if (usedPhotoPort.existsByHash(hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 사진이에요. 새로 찍어 주세요.");
        }

        TerritoryScoringPort.Result result = scoringPort.score(c.adjective(), c.noun(), criteria, c.image(), c.contentType());
        if (result.criteriaViolation()) {
            log.warn("채점 기준 위반으로 땅 세팅 거절. userId={}, dongCode={}, reason={}", userId, dongCode, result.violationReason());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "채점 기준을 사용할 수 없어요. " + result.violationReason());
        }
        if (result.score() == null || result.score() < 0 || result.score() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 채점 결과가 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
        }

        String key = storageUseCase.uploadImage(c.image(), c.fileName(), c.contentType());
        Territory saved = writer.apply(territory.getTerritoryId(), userId, c.adjective(), c.noun(), criteria,
                key, hash, result.score());
        return new SetupResultView(dongCode, saved.getMission(), saved.getMissionAdjective(), saved.getMissionNoun(),
                saved.getScoringCriteria(), saved.getDefensePhoto(), saved.getDefenseScore(), result.review());
    }
}
