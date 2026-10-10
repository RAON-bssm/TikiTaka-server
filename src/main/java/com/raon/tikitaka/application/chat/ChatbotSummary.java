package com.raon.tikitaka.application.chat;

import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.domain.chat.Chatbot;

import java.time.LocalDateTime;

/**
 * 챗봇 목록 화면 한 줄. 마지막 메시지는 아직 대화한 적 없으면 null이다.
 * equipment는 착용한 게 없으면 빈 목록·빈 맵이다(null 아님).
 */
public record ChatbotSummary(Chatbot chatbot, String lastMessage, LocalDateTime lastMessageAt, EquippedItems equipment) {
}
