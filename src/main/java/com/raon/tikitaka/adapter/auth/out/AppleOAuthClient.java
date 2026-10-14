package com.raon.tikitaka.adapter.auth.out;

import com.raon.tikitaka.application.auth.SocialUserInfo;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.global.exception.SocialLoginFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Apple은 사용자 정보 조회 API가 없어서, 앱이 받은 identity token(JWT)을 Apple 공개키로 직접 검증한다.
 * 요청의 provider_access_token에 identityToken이 그대로 들어오고, 유저 식별자는 sub만 쓴다
 * (이메일은 사용자가 가릴 수 있어 식별자로 쓰지 않는다).
 */
@Component
public class AppleOAuthClient implements ProviderOAuthClient {

    static final String ISSUER = "https://appleid.apple.com";
    private static final String JWK_SET_URI = ISSUER + "/auth/keys";

    private final NimbusJwtDecoder jwtDecoder;

    /**
     * 공개키 목록은 NimbusJwtDecoder가 캐시한다(기본 5분, 모르는 kid가 오면 다시 받아온다).
     */
    @Autowired
    public AppleOAuthClient(@Value("${apple.client-id}") String clientId) {
        this(NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).jwsAlgorithm(SignatureAlgorithm.RS256).build(), clientId);
    }

    /** 테스트에서 공개키 출처만 바꿔 끼우기 위한 생성자. 검증 규칙은 운영과 같다. */
    AppleOAuthClient(NimbusJwtDecoder jwtDecoder, String clientId) {
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setAllowEmptyExpiryClaim(false);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(ISSUER),
                new JwtAudienceValidator(clientId)));   // aud == 앱 번들 ID
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public LoginProvider getProvider() {
        return LoginProvider.APPLE;
    }

    @Override
    public SocialUserInfo fetchUserInfo(String providerAccessToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(providerAccessToken);
        } catch (BadJwtException e) {
            // 형식 오류, 서명 불일치, iss/aud 불일치, 만료
            throw new SocialLoginFailedException(LoginProvider.APPLE, e);
        } catch (JwtException e) {
            // BadJwtException이 아닌 JwtException은 Apple 공개키(JWKS) 조회 실패다. 토큰 문제가 아니므로 503
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Apple 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.", e);
        }

        String appleId = jwt.getSubject();
        if (!StringUtils.hasText(appleId)) {
            throw new SocialLoginFailedException(LoginProvider.APPLE, null);
        }
        return new SocialUserInfo(LoginProvider.APPLE, appleId);
    }
}
