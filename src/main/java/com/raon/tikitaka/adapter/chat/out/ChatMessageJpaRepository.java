package com.raon.tikitaka.adapter.chat.out;

import com.raon.tikitaka.domain.chat.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * 최신부터. 커서가 없을 때 쓴다.
     *
     * 커서 유무를 ":before is null or ..." 한 줄로 합치지 않고 메서드를 나눴다.
     * 그렇게 하면 null 파라미터의 타입을 추론하지 못해 드라이버가 걸리는 경우가 있다.
     */
    @Query("""
            select m from ChatMessage m
            where m.user.userId = :userId
              and m.chatbot.chatbotId = :chatbotId
            order by m.createdAt desc
            """)
    List<ChatMessage> findRecent(@Param("userId") UUID userId,
                                 @Param("chatbotId") String chatbotId,
                                 Pageable pageable);

    /** before 이전 메시지를 최신부터 */
    @Query("""
            select m from ChatMessage m
            where m.user.userId = :userId
              and m.chatbot.chatbotId = :chatbotId
              and m.createdAt < :before
            order by m.createdAt desc
            """)
    List<ChatMessage> findRecentBefore(@Param("userId") UUID userId,
                                       @Param("chatbotId") String chatbotId,
                                       @Param("before") LocalDateTime before,
                                       Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatMessage m where m.user.userId = :userId and m.chatbot.chatbotId = :chatbotId")
    void deleteAllByUserAndChatbot(@Param("userId") UUID userId, @Param("chatbotId") String chatbotId);
}
