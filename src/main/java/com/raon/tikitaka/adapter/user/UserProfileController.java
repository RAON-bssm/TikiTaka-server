package com.raon.tikitaka.adapter.user;

import com.raon.tikitaka.adapter.user.dto.UserProfileResponse;
import com.raon.tikitaka.application.user.in.GetUserProfileUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 프로필 조회 API. 다른 유저 관련 API는 /api/user(단수)를 쓰지만
 * 이 엔드포인트는 스펙에 맞춰 /api/users(복수)를 그대로 따른다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final GetUserProfileUseCase getUserProfileUseCase;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal UUID userId) {
        UserProfileResponse response = UserProfileResponse.from(getUserProfileUseCase.getProfile(userId));
        return ApiResponse.of(200, "프로필 조회 성공", response);
    }
}
