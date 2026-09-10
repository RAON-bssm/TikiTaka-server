package com.raon.tikitaka.global.security.jwt;

import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.UserRole;
import com.raon.tikitaka.global.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final long signupTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            @Value("${jwt.signup-token-expiration}") long signupTokenExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.signupTokenExpiration = signupTokenExpiration;
    }

    public String createAccessToken(UUID userId, UserRole role) {
        return buildToken(userId.toString(), TokenType.ACCESS, accessTokenExpiration, Map.of("role", role.name()));
    }

    public String createRefreshToken(UUID userId) {
        return buildToken(userId.toString(), TokenType.REFRESH, refreshTokenExpiration, Map.of());
    }

    public String createSignupToken(LoginProvider provider, String providerId) {
        return buildToken(providerId, TokenType.SIGNUP, signupTokenExpiration, Map.of("provider", provider.name()));
    }

    public Claims parseAccessToken(String token) {
        return parse(token, TokenType.ACCESS);
    }

    public Claims parseRefreshToken(String token) {
        return parse(token, TokenType.REFRESH);
    }

    public Claims parseSignupToken(String token) {
        return parse(token, TokenType.SIGNUP);
    }

    private String buildToken(String subject, TokenType type, long expiration, Map<String, Object> extraClaims) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("tokenType", type.name())
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * 만료를 나머지 실패와 분리해서 던진다.
     * 클라이언트는 만료면 refresh를, 그 외에는 재로그인을 해야 하므로 사유가 구분돼야 한다.
     * ExpiredJwtException이 JwtException의 하위 타입이라 반드시 먼저 잡아야 한다.
     */
    private Claims parse(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String actualType = claims.get("tokenType", String.class);
            if (!expectedType.name().equals(actualType)) {
                throw new InvalidTokenException(TokenErrorCode.TOKEN_INVALID, "잘못된 토큰 타입입니다.");
            }
            return claims;
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException(TokenErrorCode.TOKEN_EXPIRED, TokenErrorCode.TOKEN_EXPIRED.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(TokenErrorCode.TOKEN_INVALID, TokenErrorCode.TOKEN_INVALID.getMessage());
        }
    }
}
