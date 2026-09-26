package com.raon.tikitaka.application.chat.in;

import com.raon.tikitaka.application.chat.ChatMessageView;

import java.util.UUID;

/**
 * 챗봇에게 질문을 보낸다. 유저 검증, 지난 대화 로드, AI 서버 호출, 질문·답변 저장까지 한 번에 처리한다.
 */
public interface SendChatUseCase {

    /**
     * @return 저장된 assistant 메시지. 앱이 말풍선과 맞출 수 있게 id와 시각을 그대로 내려준다
     */
    ChatMessageView send(UUID userId, String chatbotId, String message);
}
