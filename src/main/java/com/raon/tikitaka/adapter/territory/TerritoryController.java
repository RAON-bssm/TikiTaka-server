package com.raon.tikitaka.adapter.territory;

import com.raon.tikitaka.application.territory.in.QueryTerritoryUseCase;
import com.raon.tikitaka.application.territory.view.CurrentCycleView;
import com.raon.tikitaka.application.territory.view.RepresentativesView;
import com.raon.tikitaka.application.territory.view.TerritoryDongsView;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * 대표 선발 결과. 임시 API, 프론트 확정 후 변경될 수 있음.
     * cycleId가 없으면 현재 사이클, locationId가 없으면 보드 전체. 선발 전이면 빈 목록이다
     */
    @GetMapping("/representatives")
    public ApiResponse<RepresentativesView> getRepresentatives(@AuthenticationPrincipal UUID userId,
                                                               @RequestParam(required = false) Long cycleId,
                                                               @RequestParam(required = false) Long locationId) {
        return ApiResponse.of(200, "대표 선발 결과 조회 성공",
                queryTerritoryUseCase.getRepresentatives(userId, cycleId, locationId));
    }
}
