package com.raon.tikitaka.adapter.chat.dto;

import com.raon.tikitaka.application.chat.ChatMessageView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 대화 목록의 한 줄. role은 user / assistant 소문자로 내려간다.
 */
public record ChatMessageResponse(
        UUID messageId,
        String role,
        String content,
        List<ChatSourceResponse> sources,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessageView view) {
        return new ChatMessageResponse(
                view.messageId(),
                view.role().name().toLowerCase(),
                view.content(),
                ChatSourceResponse.from(view.sources()),
                view.createdAt()
        );
    }
}
