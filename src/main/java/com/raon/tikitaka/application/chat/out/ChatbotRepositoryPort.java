package com.raon.tikitaka.application.chat.out;

import com.raon.tikitaka.domain.chat.Chatbot;

import java.util.List;
import java.util.Optional;

public interface ChatbotRepositoryPort {

    /** 활성 챗봇만 돌려준다. 비활성은 없는 것으로 취급한다 */
    Optional<Chatbot> findActiveById(String chatbotId);

    List<Chatbot> findAllActive();
}
