package com.raon.tikitaka.application.auth;

import com.raon.tikitaka.application.inventory.out.InventoryRepositoryPort;
import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.product.out.ProductRepositoryPort;
import com.raon.tikitaka.application.user.out.TokenRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.SignupProperties;
import com.raon.tikitaka.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
    @Mock LocationRepositoryPort locationRepositoryPort;
    @Mock ProductRepositoryPort productRepositoryPort;
    @Mock InventoryRepositoryPort inventoryRepositoryPort;
    @Mock TokenRepositoryPort tokenRepositoryPort;
    @Spy SignupProperties signupProperties = new SignupProperties(5000);
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

    @Test
    void 가입하면_5000포인트를_가지고_시작한다() {
        Claims claims = mock(Claims.class);
        given(claims.get("provider", String.class)).willReturn(LoginProvider.KAKAO.name());
        given(claims.getSubject()).willReturn(PROVIDER_ID);
        given(jwtProvider.parseSignupToken(SIGNUP_TOKEN)).willReturn(claims);
        given(userRepositoryPort.findByProviderAndProviderId(LoginProvider.KAKAO, PROVIDER_ID)).willReturn(Optional.empty());
        given(locationRepositoryPort.findById(1L)).willReturn(Optional.of(mock(Location.class)));
        given(userRepositoryPort.save(any())).willAnswer(inv -> inv.getArgument(0));

        authService.signup(SIGNUP_TOKEN, "tester", 1L);

        ArgumentCaptor<Users> saved = ArgumentCaptor.forClass(Users.class);
        verify(userRepositoryPort).save(saved.capture());
        assertThat(saved.getValue().getPoint()).isEqualTo(5000);
    }
}
