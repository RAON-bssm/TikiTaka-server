package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.domain.chat.ChatbotEquippedColor;
import com.raon.tikitaka.domain.enums.ColorGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChatbotEquippedColorJpaRepository extends JpaRepository<ChatbotEquippedColor, Long> {

    @Query("""
            select cc from ChatbotEquippedColor cc
            join fetch cc.color
            where cc.chatbot.chatbotId in :chatbotIds
            """)
    List<ChatbotEquippedColor> findAllByChatbotIdsWithColor(@Param("chatbotIds") Collection<String> chatbotIds);

    @Modifying
    @Query("""
            delete from ChatbotEquippedColor cc
            where cc.chatbot.chatbotId = :chatbotId and cc.colorGroup in :colorGroups
            """)
    void deleteAllByChatbotIdAndColorGroups(@Param("chatbotId") String chatbotId, @Param("colorGroups") Collection<ColorGroup> colorGroups);
}
