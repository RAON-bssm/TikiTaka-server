package com.raon.tikitaka.global.security;

import com.raon.tikitaka.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 필터 단계의 실패 응답을 공통 ApiResponse 형식으로 직접 쓴다.
 * response.sendError()를 쓰면 /error로 포워딩되어 스프링 기본 에러 바디가 나가므로
 * 여기서 바디를 직접 작성해 message가 클라이언트까지 전달되게 한다.
 */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, int status, String message, AuthErrorResponse data)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.of(status, message, data));
    }
}
