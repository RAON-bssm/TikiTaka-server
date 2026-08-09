package com.raon.tikitaka.adapter.auth;

import com.raon.tikitaka.adapter.auth.dto.LoginRequest;
import com.raon.tikitaka.adapter.auth.dto.LoginResponse;
import com.raon.tikitaka.adapter.auth.dto.ReissueRequest;
import com.raon.tikitaka.adapter.auth.dto.SignupRequest;
import com.raon.tikitaka.adapter.auth.dto.TokenResponse;
import com.raon.tikitaka.application.auth.LoginResult;
import com.raon.tikitaka.application.auth.TokenResult;
import com.raon.tikitaka.application.auth.in.LoginUseCase;
import com.raon.tikitaka.application.auth.in.LogoutUseCase;
import com.raon.tikitaka.application.auth.in.ReissueUseCase;
import com.raon.tikitaka.application.auth.in.SignupUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final SignupUseCase signupUseCase;
    private final ReissueUseCase reissueUseCase;
    private final LogoutUseCase logoutUseCase;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.login(request.provider(), request.providerAccessToken());
        LoginResponse response = switch (result) {
            case LoginResult.Registered r -> LoginResponse.loggedIn(r.accessToken(), r.refreshToken());
            case LoginResult.SignupRequired r -> LoginResponse.signupRequired(r.signupToken());
        };
        return ApiResponse.of(200, "로그인 처리 완료", response);
    }

    @PostMapping("/signup")
    public ApiResponse<TokenResponse> signup(@RequestBody SignupRequest request) {
        TokenResult result = signupUseCase.signup(request.signupToken(), request.userName());
        return ApiResponse.of(200, "회원가입 성공", new TokenResponse(result.accessToken(), result.refreshToken()));
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@RequestBody ReissueRequest request) {
        TokenResult result = reissueUseCase.reissue(request.refreshToken());
        return ApiResponse.of(200, "토큰 재발급 성공", new TokenResponse(result.accessToken(), result.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UUID userId) {
        logoutUseCase.logout(userId);
        return ApiResponse.of(204, "로그아웃 성공", null);
    }
}
