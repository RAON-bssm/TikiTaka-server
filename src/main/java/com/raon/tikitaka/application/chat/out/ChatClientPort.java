package com.raon.tikitaka.application.chat.out;

import com.raon.tikitaka.application.chat.ChatAnswer;
import com.raon.tikitaka.application.chat.ChatHealth;
import com.raon.tikitaka.application.chat.ChatTurn;
import com.raon.tikitaka.application.chat.ChatUserContext;
import com.raon.tikitaka.domain.chat.Chatbot;

import java.util.List;

/**
 * FastAPI AI 서버 호출. 벡터DB 검색과 Ollama 호출은 전부 그쪽 안쪽 사정이라 여기서는 모른다.
 */
public interface ChatClientPort {

    ChatAnswer ask(Chatbot chatbot, ChatUserContext user, List<ChatTurn> history, String message);

    ChatHealth health();
}
