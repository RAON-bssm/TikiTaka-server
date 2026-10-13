package com.raon.tikitaka.adapter.auth.out;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.raon.tikitaka.application.auth.SocialUserInfo;
import com.raon.tikitaka.application.auth.out.AppleRevokePort;
import com.raon.tikitaka.global.exception.SocialLoginFailedException;
import io.jsonwebtoken.Jwts;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.HttpClient;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 회원 탈퇴 시 Sign in with Apple 연동 해지.
 * 로그인 때 Apple refresh token을 저장하지 않으므로, 탈퇴 직전에 앱이 Apple 재인증으로 받은
 * authorization code를 받아서 [code → 토큰 교환 → 본인 확인 → revoke]를 한 번에 처리한다.
 *
 * Apple 토큰 API는 client_secret으로 .p8 비밀키(ES256)로 서명한 JWT를 요구한다.
 */
@Slf4j
@Component
public class AppleRevokeClient implements AppleRevokePort {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

    private final WebClient webClient;
    private final AppleOAuthClient appleOAuthClient;
    private final String clientId;
    private final String teamId;
    private final String keyId;
    private final PrivateKey privateKey;    // 설정이 비어 있으면 null. 호출 시점에 500으로 실패한다

    @Autowired
    public AppleRevokeClient(
            AppleOAuthClient appleOAuthClient,
            @Value("${apple.client-id}") String clientId,
            @Value("${apple.team-id}") String teamId,
            @Value("${apple.key-id}") String keyId,
            @Value("${apple.private-key}") String privateKey
    ) {
        this(WebClient.builder()
                        .baseUrl(AppleOAuthClient.ISSUER)
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) TIMEOUT.toMillis())
                                .responseTimeout(TIMEOUT)))
                        .build(),
                appleOAuthClient, clientId, teamId, keyId, parsePrivateKey(privateKey));
    }

    /** 테스트에서 Apple 서버 응답만 바꿔 끼우기 위한 생성자. */
    AppleRevokeClient(WebClient webClient, AppleOAuthClient appleOAuthClient,
                      String clientId, String teamId, String keyId, PrivateKey privateKey) {
        this.webClient = webClient;
        this.appleOAuthClient = appleOAuthClient;
        this.clientId = clientId;
        this.teamId = teamId;
        this.keyId = keyId;
        this.privateKey = privateKey;
    }

    @Override
    public void revoke(String authorizationCode, String appleUserId) {
        String clientSecret = createClientSecret();
        AppleTokenResponse tokens = exchange(authorizationCode, clientSecret);
        verifyOwner(tokens.idToken(), appleUserId);

        // authorization_code 교환이면 refresh token이 항상 오지만, 없으면 access token으로 해지한다
        boolean hasRefreshToken = StringUtils.hasText(tokens.refreshToken());
        String token = hasRefreshToken ? tokens.refreshToken() : tokens.accessToken();
        String tokenTypeHint = hasRefreshToken ? "refresh_token" : "access_token";
        if (!StringUtils.hasText(token)) {
            log.error("Apple 토큰 응답에 refresh_token/access_token이 없습니다.");
            throw unavailable(null);
        }

        try {
            webClient.post()
                    .uri("/auth/revoke")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("client_id", clientId)
                            .with("client_secret", clientSecret)
                            .with("token", token)
                            .with("token_type_hint", tokenTypeHint))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw failure("revoke", e);
        } catch (WebClientException e) {
            throw unavailable(e);
        }
    }

    private AppleTokenResponse exchange(String authorizationCode, String clientSecret) {
        AppleTokenResponse response;
        try {
            response = webClient.post()
                    .uri("/auth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("client_id", clientId)
                            .with("client_secret", clientSecret)
                            .with("code", authorizationCode)
                            .with("grant_type", "authorization_code"))
                    .retrieve()
                    .bodyToMono(AppleTokenResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 400 && "invalid_grant".equals(errorCode(e))) {
                // code 만료(5분) 또는 이미 사용됨. 앱이 Apple 재인증으로 새 code를 받아 다시 요청해야 한다.
                // 401을 쓰지 않는 이유: 앱의 토큰 재발급 인터셉터가 401에 반응하지 않게 하려고
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Apple 인증이 만료되었거나 이미 사용되었습니다. Apple 인증을 다시 진행해주세요.", e);
            }
            throw failure("token", e);
        } catch (WebClientException e) {
            throw unavailable(e);
        }
        if (response == null) {
            throw unavailable(null);
        }
        return response;
    }

    /**
     * code가 탈퇴하려는 유저의 Apple 계정에서 나온 것인지 확인한다.
     * 확인 없이 revoke하면, 앱이 다른 Apple 계정으로 재인증했을 때 엉뚱한 계정의 연동이 끊긴다.
     */
    private void verifyOwner(String idToken, String appleUserId) {
        if (!StringUtils.hasText(idToken)) {
            log.error("Apple 토큰 응답에 id_token이 없습니다.");
            throw unavailable(null);
        }
        SocialUserInfo owner;
        try {
            owner = appleOAuthClient.fetchUserInfo(idToken);
        } catch (SocialLoginFailedException e) {
            // Apple이 방금 직접 준 토큰이 검증에 실패하는 경우. aud(apple.client-id) 설정 불일치가 가장 유력하다
            log.error("Apple 토큰 응답의 id_token 검증 실패", e);
            throw unavailable(e);
        }
        if (!owner.providerId().equals(appleUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "현재 로그인한 계정의 Apple 인증 정보가 아닙니다. 가입한 Apple 계정으로 다시 인증해주세요.");
        }
    }

    String createClientSecret() {
        if (privateKey == null || !StringUtils.hasText(teamId) || !StringUtils.hasText(keyId)) {
            log.error("apple.team-id / apple.key-id / apple.private-key 설정이 비어 있어 Apple 연동 해지를 할 수 없습니다.");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Apple 연동 해지를 처리할 수 없습니다.");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(teamId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(CLIENT_SECRET_TTL)))
                .audience().single(AppleOAuthClient.ISSUER)
                .subject(clientId)
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    /**
     * .p8 파일 내용(PKCS#8 PEM)을 읽는다. 환경변수로 넣으면서 줄바꿈이 "\n" 문자열로 바뀌어 들어와도 받아준다.
     * 값이 있는데 형식이 틀리면 기동 시점에 바로 실패시켜 배포 설정 오류를 일찍 드러낸다.
     */
    static PrivateKey parsePrivateKey(String pem) {
        if (!StringUtils.hasText(pem)) {
            return null;
        }
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("apple.private-key 형식이 올바르지 않습니다. .p8 파일 내용을 그대로 넣어주세요.", e);
        }
    }

    /** Apple 4xx는 대부분 우리 설정 문제(invalid_client 등)라 500, 5xx는 Apple 장애라 503. */
    private ResponseStatusException failure(String api, WebClientResponseException e) {
        if (e.getStatusCode().is4xxClientError()) {
            log.error("Apple {} API 호출 실패: status={}, body={}", api, e.getStatusCode().value(), e.getResponseBodyAsString());
            return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Apple 연동 해지를 처리할 수 없습니다.", e);
        }
        return unavailable(e);
    }

    private static ResponseStatusException unavailable(Throwable cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Apple 인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.", cause);
    }

    private static String errorCode(WebClientResponseException e) {
        try {
            AppleErrorResponse body = e.getResponseBodyAs(AppleErrorResponse.class);
            return body == null ? null : body.error();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)   // token_type, expires_in 등은 쓰지 않는다
    record AppleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("id_token") String idToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppleErrorResponse(String error) {
    }
}
