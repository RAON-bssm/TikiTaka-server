package com.raon.tikitaka.application.chat.in;

import com.raon.tikitaka.application.chat.ChatHistory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 특정 챗봇과의 대화 목록. 최신부터 한 페이지씩 거슬러 올라간다.
 */
public interface GetChatHistoryUseCase {

    /**
     * @param before 이 시각 이전의 메시지만. null이면 최신부터
     */
    ChatHistory getHistory(UUID userId, String chatbotId, LocalDateTime before, Integer size);
}
