package com.raon.tikitaka.application.chat.out;

import com.raon.tikitaka.domain.chat.ChatbotEquipment;
import com.raon.tikitaka.domain.enums.ProductType;

import java.util.Collection;
import java.util.List;

public interface ChatbotEquipmentRepositoryPort {

    /** 목록 조회용. 챗봇별 건별 조회(N+1)를 피하려고 여러 챗봇을 한 번에 가져온다. product는 fetch join 된다 */
    List<ChatbotEquipment> findAllByChatbotIds(Collection<String> chatbotIds);

    void deleteAllByChatbotIdAndProductTypes(String chatbotId, Collection<ProductType> productTypes);

    List<ChatbotEquipment> saveAll(List<ChatbotEquipment> equipments);
}
