package com.raon.tikitaka.application.chat.in;

import com.raon.tikitaka.application.chat.ChatbotSummary;

import java.util.List;
import java.util.UUID;

/**
 * 대화할 수 있는 챗봇 목록. 유저별 마지막 메시지가 함께 붙어 채팅방 목록처럼 쓸 수 있다.
 */
public interface GetChatbotListUseCase {

    List<ChatbotSummary> getChatbots(UUID userId);
}
