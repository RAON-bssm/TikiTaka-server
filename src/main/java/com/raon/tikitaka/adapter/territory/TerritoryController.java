package com.raon.tikitaka.adapter.territory;

import com.raon.tikitaka.application.territory.in.QueryTerritoryUseCase;
import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.TerritoryDetailView;
import com.raon.tikitaka.application.territory.view.TerritoryDongsView;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 조회 응답은 application의 view 레코드를 그대로 쓴다(필드 가공이 없어 DTO를 따로 두지 않는다) */
@RestController
@RequestMapping("/api/territory")
@RequiredArgsConstructor
public class TerritoryController {

    private final QueryTerritoryUseCase queryTerritoryUseCase;

    @GetMapping("/cycle/current")
    public ApiResponse<CurrentCycleView> getCurrentCycle(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.of(200, "현재 사이클 조회 성공", queryTerritoryUseCase.getCurrentCycle(userId));
    }

    /** 동 정보 목록. locationId가 없으면 보드 전체 */
    @GetMapping("/dongs")
    public ApiResponse<TerritoryDongsView> getDongs(@AuthenticationPrincipal UUID userId,
                                                    @RequestParam(required = false) Long locationId) {
        return ApiResponse.of(200, "동 정보 조회 성공", queryTerritoryUseCase.getDongs(userId, locationId));
    }

    /** 동 상세. 앱이 아는 dong_code(열거형 값)로 조회한다 */
    @GetMapping("/dongs/{dongCode}")
    public ApiResponse<TerritoryDetailView> getDongDetail(@AuthenticationPrincipal UUID userId,
                                                          @PathVariable Long dongCode) {
        return ApiResponse.of(200, "동 상세 조회 성공", queryTerritoryUseCase.getDongDetail(userId, dongCode));
    }
}
