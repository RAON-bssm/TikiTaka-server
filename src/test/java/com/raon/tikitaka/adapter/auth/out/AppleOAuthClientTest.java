package com.raon.tikitaka.adapter.auth.out;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.raon.tikitaka.application.auth.SocialUserInfo;
import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.global.exception.SocialLoginFailedException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleOAuthClientTest {

    private static final String CLIENT_ID = "com.raon.tikitaka";
    private static final String APPLE_SUB = "001234.abcdef0123456789.0123";

    private static RSAKey appleKey;     // Apple 공개키 목록(JWKS)에 올라가 있는 키
    private static RSAKey otherKey;     // 같은 kid지만 Apple 키가 아닌 키

    private AppleOAuthClient client = clientWith(new ImmutableJWKSet<>(new JWKSet(appleKey.toPublicJWK())));

    @BeforeAll
    static void generateKeys() throws Exception {
        appleKey = new RSAKeyGenerator(2048).keyID("apple-kid").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("apple-kid").generate();
    }

    @Test
    void 정상_토큰이면_sub를_providerId로_돌려준다() throws Exception {
        String token = sign(appleKey, validClaims().build());

        SocialUserInfo result = client.fetchUserInfo(token);

        assertThat(result.provider()).isEqualTo(LoginProvider.APPLE);
        assertThat(result.providerId()).isEqualTo(APPLE_SUB);
    }

    @Test
    void aud가_번들ID와_다르면_401() throws Exception {
        String token = sign(appleKey, validClaims().audience("com.other.app").build());

        assertThatThrownBy(() -> client.fetchUserInfo(token)).isInstanceOf(SocialLoginFailedException.class);
    }

    @Test
    void iss가_Apple이_아니면_401() throws Exception {
        String token = sign(appleKey, validClaims().issuer("https://evil.example.com").build());

        assertThatThrownBy(() -> client.fetchUserInfo(token)).isInstanceOf(SocialLoginFailedException.class);
    }

    @Test
    void 만료된_토큰이면_401() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String token = sign(appleKey, validClaims()
                .issueTime(Date.from(past.minus(Duration.ofMinutes(10))))
                .expirationTime(Date.from(past))
                .build());

        assertThatThrownBy(() -> client.fetchUserInfo(token)).isInstanceOf(SocialLoginFailedException.class);
    }

    @Test
    void exp가_없으면_401() throws Exception {
        String token = sign(appleKey, validClaims().expirationTime(null).build());

        assertThatThrownBy(() -> client.fetchUserInfo(token)).isInstanceOf(SocialLoginFailedException.class);
    }

    @Test
    void 다른_키로_서명했으면_401() throws Exception {
        String token = sign(otherKey, validClaims().build());

        assertThatThrownBy(() -> client.fetchUserInfo(token)).isInstanceOf(SocialLoginFailedException.class);
    }

    @Test
    void JWT_형식이_아니면_401() {
        assertThatThrownBy(() -> client.fetchUserInfo("not-a-jwt")).isInstanceOf(SocialLoginFailedException.class);
    }

    @Test
    void Apple_공개키_조회에_실패하면_503() throws Exception {
        AppleOAuthClient unreachable = clientWith((selector, context) -> {
            throw new RemoteKeySourceException("Couldn't retrieve remote JWK set", null);
        });
        String token = sign(appleKey, validClaims().build());

        assertThatThrownBy(() -> unreachable.fetchUserInfo(token))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static AppleOAuthClient clientWith(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        return new AppleOAuthClient(decoder, CLIENT_ID);
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(AppleOAuthClient.ISSUER)
                .audience(CLIENT_ID)
                .subject(APPLE_SUB)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(10))));
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
