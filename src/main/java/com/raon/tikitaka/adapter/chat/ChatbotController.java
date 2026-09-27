package com.raon.tikitaka.adapter.chat;

import com.raon.tikitaka.adapter.chat.dto.ChatbotListResponse;
import com.raon.tikitaka.application.chat.in.GetChatbotListUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 챗봇 목록. 유저별 마지막 메시지가 함께 붙어 채팅방 목록처럼 쓸 수 있다.
 * 마지막 메시지가 유저마다 다르므로 인증이 필요하다.
 */
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final GetChatbotListUseCase getChatbotListUseCase;

    @GetMapping
    public ApiResponse<ChatbotListResponse> getChatbots(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.of(200, "챗봇 목록 조회 성공",
                ChatbotListResponse.from(getChatbotListUseCase.getChatbots(userId)));
    }
}
