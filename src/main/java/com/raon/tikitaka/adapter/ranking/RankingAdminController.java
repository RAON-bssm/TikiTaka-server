package com.raon.tikitaka.adapter.ranking;

import com.raon.tikitaka.application.ranking.in.FinalizeSeasonUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시즌 확정 수동 실행 API. 평소에는 새벽 스케줄러가 돌린다.
 * 휴면 전환은 실수로 호출하면 전 유저가 휴면 처리될 수 있어 엔드포인트를 만들지 않았다.
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
