package com.raon.tikitaka.adapter.match;

import com.raon.tikitaka.application.match.in.EnsureFutureStagesUseCase;
import com.raon.tikitaka.application.match.in.OpenRoundUseCase;
import com.raon.tikitaka.application.user.in.ApplyLocationSwapUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치 수동 트리거 — 평상시엔 스케줄러(9단계)가 돌리지만,
 * 배치 실패 시의 복구 경로이자 로컬 검증(10단계)의 실행 버튼이다.
 */
@RestController
@RequestMapping("/api/admin/match")
@RequiredArgsConstructor
public class MatchAdminController {

    private final EnsureFutureStagesUseCase ensureFutureStagesUseCase;
    private final OpenRoundUseCase openRoundUseCase;
    private final ApplyLocationSwapUseCase applyLocationSwapUseCase;

    @PostMapping("/ensure-stages")
    public ApiResponse<Void> ensureStages() {
        ensureFutureStagesUseCase.execute();
        return ApiResponse.of(200, "미래 라운드 생성 실행 완료", null);
    }

    @PostMapping("/open-rounds")
    public ApiResponse<Void> openRounds() {
        openRoundUseCase.execute();
        return ApiResponse.of(200, "매치·게시판 생성 실행 완료", null);
    }

    @PostMapping("/apply-swaps")
    public ApiResponse<Void> applySwaps() {
        applyLocationSwapUseCase.execute();
        return ApiResponse.of(200, "지역 스위칭 적용 실행 완료", null);
    }
}
