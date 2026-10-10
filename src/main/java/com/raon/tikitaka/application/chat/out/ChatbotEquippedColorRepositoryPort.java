package com.raon.tikitaka.application.chat.out;

import com.raon.tikitaka.domain.chat.ChatbotEquippedColor;
import com.raon.tikitaka.domain.enums.ColorGroup;

import java.util.Collection;
import java.util.List;

public interface ChatbotEquippedColorRepositoryPort {

    /** 목록 조회용. color는 fetch join 된다 */
    List<ChatbotEquippedColor> findAllByChatbotIds(Collection<String> chatbotIds);

    void deleteAllByChatbotIdAndColorGroups(String chatbotId, Collection<ColorGroup> colorGroups);

    List<ChatbotEquippedColor> saveAll(List<ChatbotEquippedColor> equippedColors);
}
