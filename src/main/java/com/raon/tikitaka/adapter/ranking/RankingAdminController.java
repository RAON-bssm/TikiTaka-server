package com.raon.tikitaka.adapter.ranking;

import com.raon.tikitaka.application.ranking.in.FinalizeSeasonUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시즌 확정 수동 트리거 — 평상시엔 스케줄러(9단계, 05:20)가 돌린다.
 * 휴면 전환(DeactivateInactiveUsers)은 엔드포인트를 일부러 만들지 않았다 —
 * 로그인 브랜치 병합 전에 실수로 호출하면 전 유저가 휴면 처리되기 때문.
 */
@RestController
@RequestMapping("/api/admin/ranking")
@RequiredArgsConstructor
public class RankingAdminController {

    private final FinalizeSeasonUseCase finalizeSeasonUseCase;

    @PostMapping("/finalize-season")
    public ApiResponse<Void> finalizeSeason() {
        finalizeSeasonUseCase.execute();
        return ApiResponse.of(200, "시즌 확정 실행 완료", null);
    }
}
