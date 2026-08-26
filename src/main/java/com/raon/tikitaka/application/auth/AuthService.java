package com.raon.tikitaka.application.auth;

import com.raon.tikitaka.application.auth.in.LoginUseCase;
import com.raon.tikitaka.application.auth.in.LogoutUseCase;
import com.raon.tikitaka.application.auth.in.ReissueUseCase;
import com.raon.tikitaka.application.auth.in.SignupUseCase;
import com.raon.tikitaka.application.auth.out.OAuthClientPort;
import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.user.out.TokenRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.token.Token;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.exception.DuplicateUserNameException;
import com.raon.tikitaka.global.exception.InvalidTokenException;
import com.raon.tikitaka.global.security.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService implements LoginUseCase, SignupUseCase, ReissueUseCase, LogoutUseCase {

    private final OAuthClientPort oAuthClientPort;
    private final UserRepositoryPort userRepositoryPort;
    private final TokenRepositoryPort tokenRepositoryPort;
    private final LocationRepositoryPort locationRepositoryPort;
    private final JwtProvider jwtProvider;

    @Override
    public LoginResult login(LoginProvider provider, String providerAccessToken) {
        SocialUserInfo socialUserInfo = oAuthClientPort.fetchUserInfo(provider, providerAccessToken);

        Optional<Users> existing = userRepositoryPort
                .findByProviderAndProviderId(provider, socialUserInfo.providerId());
        if (existing.isPresent()) {
            Users user = existing.get();
            user.touch();   // lastActiveAt 갱신. 휴면이었다면 ACTIVE로 복귀
            return new LoginResult.Registered(
                    jwtProvider.createAccessToken(user.getUserId(), user.getRole()),
                    issueAndStoreRefreshToken(user.getUserId(), provider));
        }
        return new LoginResult.SignupRequired(
                jwtProvider.createSignupToken(provider, socialUserInfo.providerId()));
    }

    @Override
    public TokenResult signup(String signupToken, String userName, Long mainLocationId) {
        Claims claims = jwtProvider.parseSignupToken(signupToken);
        LoginProvider provider = LoginProvider.valueOf(claims.get("provider", String.class));
        String providerId = claims.getSubject();

        if (userRepositoryPort.existsByUserName(userName)) {
            throw new DuplicateUserNameException(userName);
        }

        // 가입 시 동네는 필수다. main_location이 NOT NULL이라 소속 없는 유저는 만들 수 없다
        if (mainLocationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "가입할 동네를 선택해주세요.");
        }
        Optional<Location> mainLocation = locationRepositoryPort.findById(mainLocationId);
        if (mainLocation.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 동네입니다.");
        }

        Users user = userRepositoryPort.save(Users.of(userName, provider, providerId, mainLocation.get()));

        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getRole());
        String refreshToken = issueAndStoreRefreshToken(user.getUserId(), provider);
        return new TokenResult(accessToken, refreshToken);
    }

    @Override
    public TokenResult reissue(String refreshToken) {
        Claims claims = jwtProvider.parseRefreshToken(refreshToken);
        UUID userId = UUID.fromString(claims.getSubject());

        Token token = tokenRepositoryPort.findByUserId(userId)
                .orElseThrow(() -> new InvalidTokenException("로그아웃되었거나 존재하지 않는 사용자입니다."));
        if (!token.getRefreshToken().equals(refreshToken)) {
            throw new InvalidTokenException("유효하지 않은 refresh token 입니다.");
        }

        Users user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("존재하지 않는 사용자입니다."));

        String newAccessToken = jwtProvider.createAccessToken(userId, user.getRole());
        String newRefreshToken = jwtProvider.createRefreshToken(userId);
        token.updateRefreshToken(newRefreshToken);
        tokenRepositoryPort.save(token);

        return new TokenResult(newAccessToken, newRefreshToken);
    }

    @Override
    public void logout(UUID userId) {
        tokenRepositoryPort.deleteByUserId(userId);
    }

    private String issueAndStoreRefreshToken(UUID userId, LoginProvider provider) {
        String refreshToken = jwtProvider.createRefreshToken(userId);
        Token token = tokenRepositoryPort.findByUserId(userId)
                .map(existing -> {
                    existing.updateRefreshToken(refreshToken);
                    return existing;
                })
                .orElseGet(() -> Token.of(userId, provider, refreshToken));
        tokenRepositoryPort.save(token);
        return refreshToken;
    }
}
