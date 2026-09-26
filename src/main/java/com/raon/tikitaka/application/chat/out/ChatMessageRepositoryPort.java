package com.raon.tikitaka.application.chat.out;

import com.raon.tikitaka.domain.chat.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepositoryPort {

    /**
     * 최근 메시지를 최신순으로 limit개. AI 서버에 실어 보낼 이력과 목록 조회가 함께 쓴다.
     *
     * @param before null이면 최신부터
     */
    List<ChatMessage> findRecent(UUID userId, String chatbotId, LocalDateTime before, int limit);

    /** 챗봇 목록 화면의 마지막 메시지 미리보기용 */
    Optional<ChatMessage> findLatest(UUID userId, String chatbotId);

    List<ChatMessage> saveAll(List<ChatMessage> messages);

    void deleteAll(UUID userId, String chatbotId);
}
