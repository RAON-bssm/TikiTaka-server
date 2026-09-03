package com.raon.tikitaka.adapter.location;

import com.raon.tikitaka.adapter.location.dto.LocationCreateRequest;
import com.raon.tikitaka.adapter.location.dto.LocationListResponse;
import com.raon.tikitaka.adapter.location.dto.LocationResponse;
import com.raon.tikitaka.application.location.in.CreateLocationUseCase;
import com.raon.tikitaka.application.location.in.GetLocationListUseCase;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final GetLocationListUseCase getLocationListUseCase;
    private final CreateLocationUseCase createLocationUseCase;

    /**
     * 지역 전체 목록. 가입 화면에서 동네를 고르는 데 쓰이므로
     * 토큰이 없는 상태에서 호출된다. SecurityConfig에서 GET만 permitAll이다.
     */
    @GetMapping
    public ApiResponse<LocationListResponse> getLocations() {
        LocationListResponse response = LocationListResponse.from(getLocationListUseCase.getLocations());
        return ApiResponse.of(200, "지역 목록 조회 성공", response);
    }

    /**
     * 지역 등록.
     * TODO(임시): 초기 지역 데이터를 넣기 위해 인증 없이 열어두었다.
     *             SecurityConfig의 POST permitAll과 함께 걷어내고 ADMIN 전용으로 바꿔야 한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LocationResponse> createLocation(@Valid @RequestBody LocationCreateRequest request) {
        Location location = createLocationUseCase.createLocation(request.locationName());
        return ApiResponse.of(201, "지역 추가 성공", LocationResponse.from(location));
    }
}
