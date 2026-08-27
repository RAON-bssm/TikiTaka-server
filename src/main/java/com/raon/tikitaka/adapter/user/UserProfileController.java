package com.raon.tikitaka.adapter.user;

import com.raon.tikitaka.adapter.user.dto.UpdateProfileRequest;
import com.raon.tikitaka.adapter.user.dto.UserProfileResponse;
import com.raon.tikitaka.application.user.in.GetUserProfileUseCase;
import com.raon.tikitaka.application.user.in.UpdateProfileUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 프로필 조회/수정 API. 다른 유저 관련 API는 /api/user(단수)를 쓰지만
 * 이 엔드포인트는 스펙에 맞춰 /api/users(복수)를 그대로 따른다.
 * 경로에 있던 {user_id}는 다른 self-scoped API들과 동일하게 빼고
 * 토큰의 유저(@AuthenticationPrincipal)만 대상으로 한다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal UUID userId) {
        UserProfileResponse response = UserProfileResponse.from(getUserProfileUseCase.getProfile(userId));
        return ApiResponse.of(200, "프로필 조회 성공", response);
    }

    @PatchMapping("/profile")
    public ApiResponse<Void> updateProfile(@AuthenticationPrincipal UUID userId,
                                            @RequestBody UpdateProfileRequest request) {
        updateProfileUseCase.updateProfile(userId, request.userName(), request.mainLocationId(), request.subLocationId());
        return ApiResponse.of(204, "프로필 수정 성공", null);
    }
}
