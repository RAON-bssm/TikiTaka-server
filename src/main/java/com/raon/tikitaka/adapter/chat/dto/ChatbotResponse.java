package com.raon.tikitaka.adapter.chat.dto;

import com.raon.tikitaka.adapter.equipment.dto.EquipmentResponse;
import com.raon.tikitaka.application.chat.ChatbotSummary;
import com.raon.tikitaka.domain.chat.Chatbot;

import java.time.LocalDateTime;

/**
 * 챗봇 목록의 한 줄.
 *
 * personaPrompt는 의도적으로 빠져 있다. 프롬프트가 노출되면 그걸 보고 우회하는 질문을 짜기 쉽다.
 * profileImage는 URL이 아니라 S3 key라 조회 URL 발급을 거쳐야 화면에 뿌릴 수 있다.
 * equipment는 유저 장비 조회(GET /api/equipment)와 같은 형태라 앱이 같은 변환 로직으로 캐릭터를 그린다.
 */
public record ChatbotResponse(
        String chatbotId,
        String name,
        String description,
        String profileImage,
        String lastMessage,
        LocalDateTime lastMessageAt,
        EquipmentResponse equipment
) {
    public static ChatbotResponse from(ChatbotSummary summary) {
        Chatbot chatbot = summary.chatbot();
        return new ChatbotResponse(
                chatbot.getChatbotId(),
                chatbot.getName(),
                chatbot.getDescription(),
                chatbot.getProfileImage(),
                summary.lastMessage(),
                summary.lastMessageAt(),
                EquipmentResponse.from(summary.equipment())
        );
    }
}
