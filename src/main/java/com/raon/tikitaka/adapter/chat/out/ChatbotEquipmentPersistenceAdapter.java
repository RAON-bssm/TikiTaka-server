package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.application.chat.out.ChatbotEquipmentRepositoryPort;
import com.raon.tikitaka.domain.chat.ChatbotEquipment;
import com.raon.tikitaka.domain.enums.ProductType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ChatbotEquipmentPersistenceAdapter implements ChatbotEquipmentRepositoryPort {

    private final ChatbotEquipmentJpaRepository chatbotEquipmentJpaRepository;

    @Override
    public List<ChatbotEquipment> findAllByChatbotIds(Collection<String> chatbotIds) {
        if (chatbotIds.isEmpty()) {
            return List.of();
        }
        return chatbotEquipmentJpaRepository.findAllByChatbotIdsWithProduct(chatbotIds);
    }

    @Override
    public void deleteAllByChatbotIdAndProductTypes(String chatbotId, Collection<ProductType> productTypes) {
        chatbotEquipmentJpaRepository.deleteAllByChatbotIdAndProductTypes(chatbotId, productTypes);
    }

    @Override
    public List<ChatbotEquipment> saveAll(List<ChatbotEquipment> equipments) {
        return chatbotEquipmentJpaRepository.saveAll(equipments);
    }
}
