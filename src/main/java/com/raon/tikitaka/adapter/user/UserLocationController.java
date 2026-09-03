package com.raon.tikitaka.adapter.user;

import com.raon.tikitaka.adapter.user.dto.SubLocationRequest;
import com.raon.tikitaka.application.user.in.ManageLocationSwapUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 지역 스위칭 API. 서브 동네 설정과 스위칭 예약, 예약 취소를 담당한다.
 * 예약된 교환은 다음 라운드 시작 직후 00:10 배치가 일괄 적용하지만,
 * /location-swap/immediate로는 라운드 종료를 기다리지 않고 즉시 교환할 수 있다.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserLocationController {

    private final ManageLocationSwapUseCase manageLocationSwapUseCase;

    @PutMapping("/sub-location")
    public ApiResponse<Void> setSubLocation(
            @AuthenticationPrincipal UUID userId,
            @RequestBody SubLocationRequest request) {
        manageLocationSwapUseCase.setSubLocation(userId, request.locationId());
        return ApiResponse.of(200, "서브 동네 설정 성공", null);
    }

    @PostMapping("/location-swap")
    public ApiResponse<Void> requestLocationSwap(@AuthenticationPrincipal UUID userId) {
        manageLocationSwapUseCase.requestLocationSwap(userId);
        return ApiResponse.of(200, "지역 스위칭 예약 성공 - 다음 라운드 시작 시 적용됩니다", null);
    }

    @DeleteMapping("/location-swap")
    public ApiResponse<Void> cancelLocationSwap(@AuthenticationPrincipal UUID userId) {
        manageLocationSwapUseCase.cancelLocationSwap(userId);
        return ApiResponse.of(200, "지역 스위칭 예약 취소 성공", null);
    }

    @PostMapping("/location-swap/immediate")
    public ApiResponse<Void> swapLocationImmediately(@AuthenticationPrincipal UUID userId) {
        manageLocationSwapUseCase.swapLocationImmediately(userId);
        return ApiResponse.of(200, "지역 스위칭 즉시 적용 성공", null);
    }
}
