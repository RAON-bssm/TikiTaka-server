package com.raon.tikitaka.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증은 됐지만 권한이 모자란 요청의 403 응답.
 * 401과 달리 재발급으로 풀리지 않으므로 클라이언트가 구분할 수 있게 별도 code를 준다.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorWriter errorWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(response, HttpStatus.FORBIDDEN.value(), "이 작업을 수행할 권한이 없습니다.",
                AuthErrorResponse.of(AuthErrorResponse.ACCESS_DENIED));
    }
}
