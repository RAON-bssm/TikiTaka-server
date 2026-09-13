package com.raon.tikitaka.adapter.user;

import com.raon.tikitaka.adapter.user.dto.LocationChangeRequest;
import com.raon.tikitaka.application.user.in.ManageLocationChangeUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 지역 변경 예약 API. 예약과 취소만 담당하고, 실제 이동은 다음 라운드 시작 직후
 * 00:10 배치가 일괄 적용한다. 예약 현황은 GET /api/user/me의 pending_location으로 본다.
 * 프로필 수정(PATCH /api/users/profile)의 main_location_id도 같은 예약으로 처리된다.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserLocationController {

    private final ManageLocationChangeUseCase manageLocationChangeUseCase;

    @PostMapping("/location-change")
    public ApiResponse<Void> reserveLocationChange(
            @AuthenticationPrincipal UUID userId,
            @RequestBody LocationChangeRequest request) {
        manageLocationChangeUseCase.reserve(userId, request.locationId());
        return ApiResponse.of(200, "지역 변경 예약 성공 - 다음 라운드 시작 시 적용됩니다", null);
    }

    @DeleteMapping("/location-change")
    public ApiResponse<Void> cancelLocationChange(@AuthenticationPrincipal UUID userId) {
        manageLocationChangeUseCase.cancel(userId);
        return ApiResponse.of(200, "지역 변경 예약 취소 성공", null);
    }
}
