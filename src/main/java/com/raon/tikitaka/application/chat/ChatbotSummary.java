package com.raon.tikitaka.application.chat;

import com.raon.tikitaka.domain.chat.Chatbot;

import java.time.LocalDateTime;

/**
 * 챗봇 목록 화면 한 줄. 마지막 메시지는 아직 대화한 적 없으면 null이다.
 */
public record ChatbotSummary(Chatbot chatbot, String lastMessage, LocalDateTime lastMessageAt) {
}
