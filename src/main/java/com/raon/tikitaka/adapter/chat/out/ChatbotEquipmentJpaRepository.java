package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.domain.chat.ChatbotEquipment;
import com.raon.tikitaka.domain.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatbotEquipmentJpaRepository extends JpaRepository<ChatbotEquipment, Long> {

    @Query("""
            select ce from ChatbotEquipment ce
            join fetch ce.product
            where ce.chatbot.chatbotId in :chatbotIds
            """)
    List<ChatbotEquipment> findAllByChatbotIdsWithProduct(@Param("chatbotIds") Collection<String> chatbotIds);

    // 파생 delete는 엔티티를 하나씩 조회 후 지워 delete-then-insert 순서가 꼬이므로 벌크 delete로 둔다(유저 장비와 동일)
    @Modifying
    @Query("""
            delete from ChatbotEquipment ce
            where ce.chatbot.chatbotId = :chatbotId and ce.product.productType in :productTypes
            """)
    void deleteAllByChatbotIdAndProductTypes(@Param("chatbotId") String chatbotId, @Param("productTypes") Collection<ProductType> productTypes);
}
