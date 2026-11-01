package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.TerritoryQueryPort;
import com.raon.tikitaka.application.territory.out.UsedPhotoPort;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.UsedPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 땅 세팅의 DB 반영만 담당한다. S3 업로드·AI 채점(느린 외부 호출)은 트랜잭션 밖에서 끝내고
 * 여기서는 짧게 다시 확인 → 저장만 한다. 별도 빈이어야 @Transactional 프록시가 적용된다.
 */
@Component
@RequiredArgsConstructor
public class TerritorySetupWriter {

    private final TerritoryQueryPort territoryQueryPort;
    private final UsedPhotoPort usedPhotoPort;

    @Transactional
    public Territory apply(Long territoryId, UUID userId, String adjective, String noun, String criteria,
                           String photoKey, String photoHash, int score) {
        LocalDateTime now = LocalDateTime.now();
        Territory t = territoryQueryPort.findById(territoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "땅을 찾을 수 없습니다."));
        // 채점하는 사이 기간이 끝났거나 사진이 다른 요청에서 먼저 쓰였을 수 있어 다시 확인한다
        if (!t.getCycle().isSetupPeriod(now) || !t.isOwnedBy(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "땅 세팅 기간이 아니거나 내 땅이 아닙니다.");
        }
        if (usedPhotoPort.existsByHash(photoHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 사용한 사진이에요. 새로 찍어 주세요.");
        }
        t.setup(adjective, noun, criteria, photoKey, score, now);
        usedPhotoPort.save(UsedPhoto.of(photoHash, userId, now));
        return t;
    }
}
