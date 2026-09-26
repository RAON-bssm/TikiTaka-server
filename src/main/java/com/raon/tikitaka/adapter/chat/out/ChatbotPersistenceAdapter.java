package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.application.chat.out.ChatbotRepositoryPort;
import com.raon.tikitaka.domain.chat.Chatbot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChatbotPersistenceAdapter implements ChatbotRepositoryPort {

    private final ChatbotJpaRepository chatbotJpaRepository;

    @Override
    public Optional<Chatbot> findActiveById(String chatbotId) {
        return chatbotJpaRepository.findByChatbotIdAndIsActiveTrue(chatbotId);
    }

    @Override
    public List<Chatbot> findAllActive() {
        return chatbotJpaRepository.findAllByIsActiveTrueOrderByChatbotIdAsc();
    }
}
