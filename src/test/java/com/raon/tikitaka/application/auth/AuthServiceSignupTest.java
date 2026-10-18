package com.raon.tikitaka.application.auth;

import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceSignupTest {

    private static final String SIGNUP_TOKEN = "signup-token";
    private static final String PROVIDER_ID = "123456789";

    @Mock UserRepositoryPort userRepositoryPort;
    @Mock JwtProvider jwtProvider;
    @InjectMocks AuthService authService;

    @Test
    void 같은_소셜_계정으로_이미_가입했으면_닉네임_검사보다_먼저_409() {
        Claims claims = mock(Claims.class);
        given(claims.get("provider", String.class)).willReturn(LoginProvider.KAKAO.name());
        given(claims.getSubject()).willReturn(PROVIDER_ID);
        given(jwtProvider.parseSignupToken(SIGNUP_TOKEN)).willReturn(claims);
        given(userRepositoryPort.findByProviderAndProviderId(LoginProvider.KAKAO, PROVIDER_ID))
                .willReturn(Optional.of(Users.of("tester", LoginProvider.KAKAO, PROVIDER_ID, null)));

        assertThatThrownBy(() -> authService.signup(SIGNUP_TOKEN, "tester", 1L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("이미 가입된 계정입니다.");
                });
        verify(userRepositoryPort, never()).existsByUserName(any());
        verify(userRepositoryPort, never()).save(any());
    }
}
