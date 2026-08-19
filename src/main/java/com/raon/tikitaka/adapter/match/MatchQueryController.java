package com.raon.tikitaka.adapter.match;

import com.raon.tikitaka.adapter.match.dto.MatchResultListResponse;
import com.raon.tikitaka.adapter.match.dto.StageResponse;
import com.raon.tikitaka.application.match.in.GetCurrentStageUseCase;
import com.raon.tikitaka.application.match.in.GetMatchResultsUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시즌 라운드와 경기 결과 조회 API. URL과 응답 형식은 API 명세서를 따른다.
 * 인증은 JWT 필터가 처리하고 로그인한 유저면 누구나 조회할 수 있다.
 */
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchQueryController {

    private final GetCurrentStageUseCase getCurrentStageUseCase;
    private final GetMatchResultsUseCase getMatchResultsUseCase;

    @GetMapping("/stage")
    public ApiResponse<StageResponse> getCurrentStage() {
        StageResponse response = StageResponse.from(getCurrentStageUseCase.getCurrentStage());
        return ApiResponse.of(200, "시즌 및 라운드 조회 성공", response);
    }

    @GetMapping("/result")
    public ApiResponse<MatchResultListResponse> getMatchResults() {
        MatchResultListResponse response = MatchResultListResponse.from(getMatchResultsUseCase.getMatchResults());
        return ApiResponse.of(200, "경기 결과 조회 성공", response);
    }
}
