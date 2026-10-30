package com.raon.tikitaka.adapter.territory;

import com.raon.tikitaka.adapter.territory.dto.DongPreferenceRequest;
import com.raon.tikitaka.adapter.territory.dto.DongPreferenceResponse;
import com.raon.tikitaka.application.territory.in.ManageDongPreferenceUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/territory/preferences")
@RequiredArgsConstructor
public class DongPreferenceController {

    private final ManageDongPreferenceUseCase manageDongPreferenceUseCase;

    @GetMapping
    public ApiResponse<DongPreferenceResponse> getPreferences(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.of(200, "희망 동 조회 성공",
                DongPreferenceResponse.from(manageDongPreferenceUseCase.getPreferences(userId)));
    }

    @PutMapping
    public ApiResponse<DongPreferenceResponse> replacePreferences(@AuthenticationPrincipal UUID userId,
                                                                  @RequestBody DongPreferenceRequest request) {
        return ApiResponse.of(200, "희망 동 등록 성공",
                DongPreferenceResponse.from(manageDongPreferenceUseCase.replacePreferences(userId, request.dongIdsOrEmpty())));
    }
}
