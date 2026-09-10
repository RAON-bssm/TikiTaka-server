package com.raon.tikitaka.global.security.jwt;

import com.raon.tikitaka.domain.enums.UserRole;
import com.raon.tikitaka.global.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 토큰 검증 실패 사유를 담아두는 request attribute 키.
     * 필터에서 예외를 던지면 ExceptionTranslationFilter보다 앞이라 잡히지 않고,
     * @RestControllerAdvice도 DispatcherServlet 바깥이라 닿지 않는다.
     * 그래서 사유만 남기고 통과시킨 뒤 JwtAuthenticationEntryPoint가 꺼내 쓴다.
     */
    public static final String TOKEN_ERROR_ATTRIBUTE = "tikitaka.tokenError";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtProvider.parseAccessToken(token);
                UUID userId = UUID.fromString(claims.getSubject());
                UserRole role = UserRole.valueOf(claims.get("role", String.class));

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException e) {
                // 여기서 401을 내지 않는다. permitAll 경로는 토큰이 깨져도 비로그인으로 계속 진행돼야 한다.
                // 보호된 경로라면 뒤의 AuthorizationFilter가 걸러내고 EntryPoint가 이 사유로 401을 만든다.
                SecurityContextHolder.clearContext();
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, e.getCode());
            }
        }
        filterChain.doFilter(request, response);
    }
}
