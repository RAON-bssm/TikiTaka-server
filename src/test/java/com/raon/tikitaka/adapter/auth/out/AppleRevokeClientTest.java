package com.raon.tikitaka.adapter.auth.out;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleRevokeClientTest {

    private static final String CLIENT_ID = "com.raon.tikitaka";
    private static final String TEAM_ID = "TEAM123456";
    private static final String KEY_ID = "KEY1234567";
    private static final String APPLE_SUB = "001234.abcdef0123456789.0123";

    private static RSAKey appleKey;     // Apple이 id_token 서명에 쓰는 키 (JWKS에 올라가 있음)
    private static KeyPair p8Key;       // 우리 .p8 키 (client_secret 서명용)

    private final List<String> calledPaths = new ArrayList<>();
    private final Map<String, StubResponse> responses = new HashMap<>();
    private AppleOAuthClient appleOAuthClient;

    @BeforeAll
    static void generateKeys() throws Exception {
        appleKey = new RSAKeyGenerator(2048).keyID("apple-kid").generate();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        p8Key = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(new JWKSet(appleKey.toPublicJWK())))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        appleOAuthClient = new AppleOAuthClient(decoder, CLIENT_ID);
    }

    @Test
    void 코드를_교환하고_본인이면_refresh_token으로_revoke한다() throws Exception {
        tokenEndpointReturns(HttpStatus.OK, tokenJson(idToken(APPLE_SUB)));
        revokeEndpointReturns(HttpStatus.OK, "");

        client(p8Key.getPrivate()).revoke("auth-code", APPLE_SUB);

        assertThat(calledPaths).containsExactly("/auth/token", "/auth/revoke");
    }

    @Test
    void 코드가_만료됐거나_이미_쓰였으면_400이고_revoke하지_않는다() {
        tokenEndpointReturns(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_grant\"}");

        assertStatus(() -> client(p8Key.getPrivate()).revoke("expired-code", APPLE_SUB), HttpStatus.BAD_REQUEST);
        assertThat(calledPaths).containsExactly("/auth/token");
    }

    @Test
    void 다른_Apple_계정의_코드면_400이고_revoke하지_않는다() throws Exception {
        tokenEndpointReturns(HttpStatus.OK, tokenJson(idToken("999999.other.0000")));

        assertStatus(() -> client(p8Key.getPrivate()).revoke("auth-code", APPLE_SUB), HttpStatus.BAD_REQUEST);
        assertThat(calledPaths).containsExactly("/auth/token");
    }

    @Test
    void 우리_설정_문제로_Apple이_4xx를_주면_500() {
        tokenEndpointReturns(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_client\"}");

        assertStatus(() -> client(p8Key.getPrivate()).revoke("auth-code", APPLE_SUB), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 토큰_교환에서_Apple_서버_장애면_503() {
        tokenEndpointReturns(HttpStatus.BAD_GATEWAY, "");

        assertStatus(() -> client(p8Key.getPrivate()).revoke("auth-code", APPLE_SUB), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void revoke에서_Apple_서버_장애면_503() throws Exception {
        tokenEndpointReturns(HttpStatus.OK, tokenJson(idToken(APPLE_SUB)));
        revokeEndpointReturns(HttpStatus.SERVICE_UNAVAILABLE, "");

        assertStatus(() -> client(p8Key.getPrivate()).revoke("auth-code", APPLE_SUB), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void p8_키_설정이_없으면_Apple을_호출하지_않고_500() {
        assertStatus(() -> client(null).revoke("auth-code", APPLE_SUB), HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(calledPaths).isEmpty();
    }

    @Test
    void client_secret은_Apple_규격대로_ES256으로_서명된다() {
        String secret = client(p8Key.getPrivate()).createClientSecret();

        Jws<Claims> jws = Jwts.parser().verifyWith(p8Key.getPublic()).build().parseSignedClaims(secret);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(jws.getHeader().getKeyId()).isEqualTo(KEY_ID);
        assertThat(jws.getPayload().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(jws.getPayload().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jws.getPayload().getExpiration()).isAfter(new Date());
        // Apple 문서 규격: aud는 배열이 아니라 문자열
        String payload = new String(Base64.getUrlDecoder().decode(secret.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"aud\":\"https://appleid.apple.com\"");
    }

    @Test
    void p8_파일_내용은_줄바꿈이_그대로든_문자열_n이든_읽는다() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(p8Key.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";

        assertThat(AppleRevokeClient.parsePrivateKey(pem)).isEqualTo(p8Key.getPrivate());
        assertThat(AppleRevokeClient.parsePrivateKey(pem.replace("\n", "\\n"))).isEqualTo(p8Key.getPrivate());
        assertThat(AppleRevokeClient.parsePrivateKey("")).isNull();
    }

    @Test
    void p8_형식이_틀리면_기동_시점에_실패한다() {
        assertThatThrownBy(() -> AppleRevokeClient.parsePrivateKey("not-a-key"))
                .isInstanceOf(IllegalStateException.class);
    }

    private AppleRevokeClient client(PrivateKey privateKey) {
        WebClient webClient = WebClient.builder()
                .baseUrl(AppleOAuthClient.ISSUER)
                .exchangeFunction(request -> {
                    String path = request.url().getPath();
                    calledPaths.add(path);
                    StubResponse stub = responses.get(path);
                    return Mono.just(ClientResponse.create(stub.status())
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(stub.body())
                            .build());
                })
                .build();
        return new AppleRevokeClient(webClient, appleOAuthClient, CLIENT_ID, TEAM_ID, KEY_ID, privateKey);
    }

    private void tokenEndpointReturns(HttpStatus status, String body) {
        responses.put("/auth/token", new StubResponse(status, body));
    }

    private void revokeEndpointReturns(HttpStatus status, String body) {
        responses.put("/auth/revoke", new StubResponse(status, body));
    }

    private static String tokenJson(String idToken) {
        return "{\"access_token\":\"apple-access\",\"token_type\":\"Bearer\",\"expires_in\":3600,"
                + "\"refresh_token\":\"apple-refresh\",\"id_token\":\"" + idToken + "\"}";
    }

    private static String idToken(String sub) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(AppleOAuthClient.ISSUER)
                .audience(CLIENT_ID)
                .subject(sub)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(10))))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(appleKey.getKeyID()).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner(appleKey));
        return jwt.serialize();
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private record StubResponse(HttpStatus status, String body) {
    }
}
