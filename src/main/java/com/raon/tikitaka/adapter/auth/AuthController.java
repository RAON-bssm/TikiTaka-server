package com.raon.tikitaka.adapter.auth;

import com.raon.tikitaka.adapter.auth.dto.LoginRequest;
import com.raon.tikitaka.adapter.auth.dto.LoginResponse;
import com.raon.tikitaka.adapter.auth.dto.ReissueRequest;
import com.raon.tikitaka.adapter.auth.dto.SignupRequest;
import com.raon.tikitaka.adapter.auth.dto.TokenResponse;
import com.raon.tikitaka.adapter.auth.dto.UserNameAvailabilityResponse;
import com.raon.tikitaka.application.auth.LoginResult;
import com.raon.tikitaka.application.auth.TokenResult;
import com.raon.tikitaka.application.auth.in.CheckUserNameUseCase;
import com.raon.tikitaka.application.auth.in.LoginUseCase;
import com.raon.tikitaka.application.auth.in.LogoutUseCase;
import com.raon.tikitaka.application.auth.in.ReissueUseCase;
import com.raon.tikitaka.application.auth.in.SignupUseCase;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final SignupUseCase signupUseCase;
    private final ReissueUseCase reissueUseCase;
    private final LogoutUseCase logoutUseCase;
    private final CheckUserNameUseCase checkUserNameUseCase;

    @PostMapping("/api/login/{provider}")
    public ApiResponse<LoginResponse> login(@PathVariable LoginProvider provider, @RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.login(provider, request.providerAccessToken());
        LoginResponse response = switch (result) {
            case LoginResult.Registered r -> LoginResponse.loggedIn(r.accessToken(), r.refreshToken());
            case LoginResult.SignupRequired r -> LoginResponse.signupRequired(r.signupToken());
        };
        return ApiResponse.of(200, "로그인 처리 완료", response);
    }

    /**
     * 가입 화면의 닉네임 중복확인. 가입 전에 호출되므로 SecurityConfig에서 permitAll이다.
     * available이 true여도 가입 요청의 409(DuplicateUserName)는 여전히 처리해야 한다.
     */
    @GetMapping("/api/auth/check-name")
    public ApiResponse<UserNameAvailabilityResponse> checkUserName(@RequestParam("user_name") String userName) {
        boolean available = checkUserNameUseCase.isUserNameAvailable(userName);
        String message = available ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다.";
        return ApiResponse.of(200, message, UserNameAvailabilityResponse.of(available));
    }

    @PostMapping("/api/auth/signup")
    public ApiResponse<TokenResponse> signup(@RequestBody SignupRequest request) {
        TokenResult result = signupUseCase.signup(request.signupToken(), request.userName(), request.mainLocationId());
        return ApiResponse.of(200, "회원가입 성공", new TokenResponse(result.accessToken(), result.refreshToken()));
    }

    @PostMapping("/api/auth/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody ReissueRequest request) {
        TokenResult result = reissueUseCase.reissue(request.refreshToken());
        return ApiResponse.of(200, "토큰 재발급 성공", new TokenResponse(result.accessToken(), result.refreshToken()));
    }

    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UUID userId) {
        logoutUseCase.logout(userId);
        return ApiResponse.of(204, "로그아웃 성공", null);
    }
}
