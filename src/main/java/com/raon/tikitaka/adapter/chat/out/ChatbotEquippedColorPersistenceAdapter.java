package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.application.chat.out.ChatbotEquippedColorRepositoryPort;
import com.raon.tikitaka.domain.chat.ChatbotEquippedColor;
import com.raon.tikitaka.domain.enums.ColorGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatbotEquippedColorPersistenceAdapter implements ChatbotEquippedColorRepositoryPort {

    private final ChatbotEquippedColorJpaRepository chatbotEquippedColorJpaRepository;

    @Override
    public List<ChatbotEquippedColor> findAllByChatbotIds(Collection<String> chatbotIds) {
        if (chatbotIds.isEmpty()) {
            return List.of();
        }
        return chatbotEquippedColorJpaRepository.findAllByChatbotIdsWithColor(chatbotIds);
    }

    @Override
    public void deleteAllByChatbotIdAndColorGroups(String chatbotId, Collection<ColorGroup> colorGroups) {
        chatbotEquippedColorJpaRepository.deleteAllByChatbotIdAndColorGroups(chatbotId, colorGroups);
    }

    @Override
    public List<ChatbotEquippedColor> saveAll(List<ChatbotEquippedColor> equippedColors) {
        return chatbotEquippedColorJpaRepository.saveAll(equippedColors);
    }
}
