package com.raon.tikitaka.adapter.territory;

import com.raon.tikitaka.application.territory.in.EnsureTerritoryCyclesUseCase;
import com.raon.tikitaka.application.territory.in.SelectRepresentativesUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 땅따먹기 배치 수동 실행. 배치 실패 복구와 스테이징 리허설용. /api/admin/**은 ADMIN만 */
@RestController
@RequestMapping("/api/admin/territory")
@RequiredArgsConstructor
public class TerritoryAdminController {

    private final EnsureTerritoryCyclesUseCase ensureTerritoryCyclesUseCase;
    private final SelectRepresentativesUseCase selectRepresentativesUseCase;

    @PostMapping("/ensure-cycles")
    public ApiResponse<Void> ensureCycles() {
        ensureTerritoryCyclesUseCase.execute();
        return ApiResponse.of(200, "사이클 생성 실행 완료", null);
    }

    /** 랭킹 동결 시각이 지난 사이클만 처리한다. 이미 선발한 사이클은 건너뛴다 */
    @PostMapping("/select-representatives")
    public ApiResponse<Void> selectRepresentatives() {
        selectRepresentativesUseCase.execute();
        return ApiResponse.of(200, "대표 선발 실행 완료", null);
    }
}
