package com.raon.tikitaka.domain.chat;

import com.raon.tikitaka.domain.enums.ChatRole;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 유저와 챗봇이 주고받은 메시지 한 건. 질문과 답변이 같은 테이블에 role로 구분되어 쌓인다.
 *
 * 대화방은 (user_id, chatbot_id) 조합 그 자체라 별도 세션 테이블을 두지 않았다.
 * 마지막 메시지와 시각도 이 테이블에서 뽑으므로 방을 언제 만들지 고민할 필요가 없다.
 */
@Entity
@Table(
        name = "chat_message",
        indexes = @Index(name = "idx_chat_message_user_bot_time", columnList = "user_id, chatbot_id, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id", columnDefinition = "uuid")
    private UUID messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ChatRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /**
     * 답변의 근거가 된 동네 정보를 JSON 문자열로 담는다. 벡터DB를 붙이기 전까지는 null이다.
     * 나중에 컬럼을 추가하면 그전에 쌓인 답변들은 근거가 영원히 빈칸이 되므로 지금부터 자리를 잡아둔다.
     */
    @Column(name = "sources", columnDefinition = "text")
    private String sources;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ChatMessage ofUser(Users user, Chatbot chatbot, String content) {
        return of(user, chatbot, ChatRole.USER, content, null);
    }

    public static ChatMessage ofAssistant(Users user, Chatbot chatbot, String content, String sources) {
        return of(user, chatbot, ChatRole.ASSISTANT, content, sources);
    }

    private static ChatMessage of(Users user, Chatbot chatbot, ChatRole role, String content, String sources) {
        ChatMessage message = new ChatMessage();
        message.user = user;
        message.chatbot = chatbot;
        message.role = role;
        message.content = content;
        message.sources = sources;
        message.createdAt = LocalDateTime.now();
        return message;
    }
}
