package com.raon.tikitaka.adapter.location;

import com.raon.tikitaka.adapter.location.dto.LocationListResponse;
import com.raon.tikitaka.application.location.in.GetLocationListUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final GetLocationListUseCase getLocationListUseCase;

    /**
     * 지역 전체 목록. 가입 화면에서 동네를 고르는 데 쓰이므로
     * 토큰이 없는 상태에서 호출된다. SecurityConfig에서 GET만 permitAll이다.
     */
    @GetMapping
    public ApiResponse<LocationListResponse> getLocations() {
        LocationListResponse response = LocationListResponse.from(getLocationListUseCase.getLocations());
        return ApiResponse.of(200, "지역 목록 조회 성공", response);
    }
}
