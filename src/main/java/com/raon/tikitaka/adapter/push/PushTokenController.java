package com.raon.tikitaka.adapter.push;

import com.raon.tikitaka.adapter.push.dto.PushTokenRequest;
import com.raon.tikitaka.application.push.in.PushTokenUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * FCM 토큰 등록·해제. 로그인이 필요하다(SecurityConfig의 anyRequest().authenticated()).
 *
 * 앱 규칙
 * - PUT: 앱 실행(로그인 상태), 로그인 성공 직후, 토큰 갱신(onTokenRefresh) 때마다 부른다. 여러 번 불러도 결과가 같다
 * - DELETE: 로그아웃 API를 부르기 전에 부른다(Access Token이 살아 있어야 하므로)
 */
@RestController
@RequestMapping("/api/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushTokenUseCase pushTokenUseCase;

    @PutMapping
    public ApiResponse<Void> register(@AuthenticationPrincipal UUID userId, @RequestBody PushTokenRequest request) {
        pushTokenUseCase.register(userId, request.token(), request.platform());
        return ApiResponse.of(200, "푸시 토큰 등록 성공", null);
    }

    @DeleteMapping
    public ApiResponse<Void> unregister(@AuthenticationPrincipal UUID userId, @RequestBody PushTokenRequest request) {
        pushTokenUseCase.unregister(userId, request.token());
        return ApiResponse.of(200, "푸시 토큰 삭제 성공", null);
    }
}
