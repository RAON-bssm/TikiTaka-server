package com.raon.tikitaka.adapter.chat.dto;

import com.raon.tikitaka.application.chat.ChatMessageView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 질문 요청 응답. 저장된 assistant 메시지를 그대로 돌려준다.
 * message_id를 주므로 앱이 낙관적으로 그려둔 말풍선과 맞출 수 있다.
 */
public record ChatAnswerResponse(
        UUID messageId,
        String reply,
        List<ChatSourceResponse> sources,
        LocalDateTime createdAt
) {
    public static ChatAnswerResponse from(ChatMessageView view) {
        return new ChatAnswerResponse(
                view.messageId(),
                view.content(),
                ChatSourceResponse.from(view.sources()),
                view.createdAt()
        );
    }
}
