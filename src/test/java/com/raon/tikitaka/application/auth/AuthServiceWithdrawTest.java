package com.raon.tikitaka.application.auth;

import com.raon.tikitaka.application.auth.out.AppleRevokePort;
import com.raon.tikitaka.application.user.out.TokenRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceWithdrawTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String APPLE_SUB = "001234.abcdef0123456789.0123";

    @Mock UserRepositoryPort userRepositoryPort;
    @Mock TokenRepositoryPort tokenRepositoryPort;
    @Mock AppleRevokePort appleRevokePort;
    @InjectMocks AuthService authService;

    @Test
    void Apple_유저는_원래_Apple_식별자로_연동_해지한_뒤_탈퇴한다() {
        Users user = userOf(LoginProvider.APPLE, APPLE_SUB);

        authService.withdraw(USER_ID, "auth-code");

        // 익명화 전의 providerId로 호출됐다 = revoke가 user.withdraw()보다 먼저 실행됐다
        verify(appleRevokePort).revoke("auth-code", APPLE_SUB);
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        verify(tokenRepositoryPort).deleteByUserId(USER_ID);
    }

    @Test
    void Apple_유저가_code_없이_탈퇴하면_400이고_아무것도_바뀌지_않는다() {
        Users user = userOf(LoginProvider.APPLE, APPLE_SUB);

        assertThatThrownBy(() -> authService.withdraw(USER_ID, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(appleRevokePort, never()).revoke(any(), any());
        assertThat(user.getStatus()).isNotEqualTo(UserStatus.WITHDRAWN);
        verify(tokenRepositoryPort, never()).deleteByUserId(any());
    }

    @Test
    void Apple_연동_해지가_실패하면_탈퇴하지_않는다() {
        Users user = userOf(LoginProvider.APPLE, APPLE_SUB);
        willThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .given(appleRevokePort).revoke("auth-code", APPLE_SUB);

        assertThatThrownBy(() -> authService.withdraw(USER_ID, "auth-code"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(user.getStatus()).isNotEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getProviderId()).isEqualTo(APPLE_SUB);
        verify(tokenRepositoryPort, never()).deleteByUserId(any());
    }

    @Test
    void 카카오_유저는_code_없이_기존대로_탈퇴한다() {
        Users user = userOf(LoginProvider.KAKAO, "123456789");

        authService.withdraw(USER_ID, null);

        verify(appleRevokePort, never()).revoke(any(), any());
        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        verify(tokenRepositoryPort).deleteByUserId(USER_ID);
    }

    private Users userOf(LoginProvider provider, String providerId) {
        Users user = Users.of("tester", provider, providerId, null);
        given(userRepositoryPort.findById(USER_ID)).willReturn(Optional.of(user));
        return user;
    }
}
