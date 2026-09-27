package com.raon.tikitaka.application.chat;

import com.raon.tikitaka.domain.chat.ChatMessage;
import com.raon.tikitaka.domain.enums.ChatRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 밖으로 내보내는 메시지 한 건. 엔티티의 sources JSON 문자열을 풀어둔 형태라
 * 어댑터가 JSON을 다시 파싱할 일이 없다.
 */
public record ChatMessageView(
        UUID messageId,
        ChatRole role,
        String content,
        List<ChatSource> sources,
        LocalDateTime createdAt
) {
    public static ChatMessageView of(ChatMessage message, List<ChatSource> sources) {
        return new ChatMessageView(
                message.getMessageId(),
                message.getRole(),
                message.getContent(),
                sources,
                message.getCreatedAt()
        );
    }
}
