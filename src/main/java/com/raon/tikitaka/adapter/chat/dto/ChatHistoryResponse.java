package com.raon.tikitaka.adapter.chat.dto;

import com.raon.tikitaka.application.chat.ChatHistory;
import com.raon.tikitaka.application.chat.ChatMessageView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 대화 목록 응답. messages는 오래된 것부터 최신 순이라 그대로 말풍선 목록에 붙이면 된다.
 *
 * @param nextBefore 다음 페이지를 요청할 때 before에 넣는 값. 더 없으면 생략된다
 */
public record ChatHistoryResponse(
        String chatbotId,
        List<ChatMessageResponse> messages,
        LocalDateTime nextBefore,
        boolean hasMore
) {
    public static ChatHistoryResponse from(String chatbotId, ChatHistory history) {
        List<ChatMessageResponse> messages = new ArrayList<>();
        for (ChatMessageView view : history.messages()) {
            messages.add(ChatMessageResponse.from(view));
        }
        return new ChatHistoryResponse(chatbotId, messages, history.nextBefore(), history.hasMore());
    }
}
