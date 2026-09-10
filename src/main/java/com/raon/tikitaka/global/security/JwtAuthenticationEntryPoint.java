package com.raon.tikitaka.global.security;

import com.raon.tikitaka.global.security.jwt.JwtAuthenticationFilter;
import com.raon.tikitaka.global.security.jwt.TokenErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증되지 않은 요청이 보호된 엔드포인트에 도달했을 때의 401 응답.
 * JwtAuthenticationFilter가 남긴 실패 사유가 있으면 그대로 싣고, 없으면 토큰 미첨부로 본다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);
        TokenErrorCode code = (attribute instanceof TokenErrorCode c) ? c : TokenErrorCode.TOKEN_MISSING;

        errorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), code.getMessage(), AuthErrorResponse.of(code));
    }
}
