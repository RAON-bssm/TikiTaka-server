package com.raon.tikitaka.adapter.user;

import com.raon.tikitaka.adapter.user.dto.UserInfoResponse;
import com.raon.tikitaka.application.user.in.GetMyInfoUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 내 정보 조회 API. 대상 유저는 토큰으로 식별하므로 별도 파라미터가 없다.
 * 랭킹에서 내 동네 강조, 마이페이지, 스위칭 화면 등 프론트 전반이 쓴다.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserInfoController {

    private final GetMyInfoUseCase getMyInfoUseCase;

    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> getMyInfo(@AuthenticationPrincipal UUID userId) {
        UserInfoResponse response = UserInfoResponse.from(getMyInfoUseCase.getMyInfo(userId));
        return ApiResponse.of(200, "내 정보 조회 성공", response);
    }
}
