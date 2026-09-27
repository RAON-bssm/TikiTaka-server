package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.domain.chat.Chatbot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatbotJpaRepository extends JpaRepository<Chatbot, String> {

    Optional<Chatbot> findByChatbotIdAndIsActiveTrue(String chatbotId);

    List<Chatbot> findAllByIsActiveTrueOrderByChatbotIdAsc();
}
