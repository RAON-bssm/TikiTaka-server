package com.raon.tikitaka.adapter.ranking;

import com.raon.tikitaka.adapter.ranking.dto.LocationRankingListResponse;
import com.raon.tikitaka.adapter.ranking.dto.UserRankingListResponse;
import com.raon.tikitaka.application.ranking.in.GetLocationRankingUseCase;
import com.raon.tikitaka.application.ranking.in.GetUserRankingUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 지역 랭킹과 개인 랭킹 조회 API. URL과 응답 형식은 API 명세서를 따른다.
 * 항상 현재 진행 중인 라운드 기준이고 순위는 저장된 값이 아니라 조회 시점에 DB가 계산한다.
 */
@RestController
@RequiredArgsConstructor
public class RankingQueryController {

    private final GetLocationRankingUseCase getLocationRankingUseCase;
    private final GetUserRankingUseCase getUserRankingUseCase;

    @GetMapping("/api/location/rank")
    public ApiResponse<LocationRankingListResponse> getLocationRanking() {
        LocationRankingListResponse response =
                LocationRankingListResponse.from(getLocationRankingUseCase.getLocationRanking());
        return ApiResponse.of(200, "지역 랭킹 조회 성공", response);
    }

    @GetMapping("/api/users/rank")
    public ApiResponse<UserRankingListResponse> getUserRanking(@AuthenticationPrincipal UUID userId) {
        UserRankingListResponse response =
                UserRankingListResponse.from(getUserRankingUseCase.getUserRanking(userId));
        return ApiResponse.of(200, "개인 랭킹 조회 성공", response);
    }
}
