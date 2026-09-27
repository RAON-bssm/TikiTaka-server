package com.raon.tikitaka.domain.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 챗봇 정의. 페르소나 프롬프트와 모델명을 들고 있고 질문 요청 때 AI 서버로 함께 전달된다.
 *
 * chatbotId는 UUID가 아니라 사람이 읽는 문자열이다. 쿼리 파람으로 노출되고
 * 시드 스크립트와 로그에서 바로 알아볼 수 있어야 해서 keyword 테이블과 같은 방식을 택했다.
 */
@Entity
@Table(name = "chatbot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chatbot {

    @Id
    @Column(name = "chatbot_id", nullable = false)
    private String chatbotId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    /** 캐릭터 페르소나. 서버 내부용이라 클라이언트로 절대 내려보내지 않는다 */
    @Column(name = "persona_prompt", nullable = false, columnDefinition = "text")
    private String personaPrompt;

    /** Ollama 모델명. 앱이 고르게 두면 노트북에 없는 모델을 부를 수 있어 서버가 잠근다 */
    @Column(name = "model", nullable = false)
    private String model;

    /** S3 key다. URL이 아니라서 화면에 쓰려면 조회 URL 발급을 거쳐야 한다 */
    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Chatbot create(String chatbotId, String name, String description,
                                 String personaPrompt, String model, String profileImage) {
        Chatbot chatbot = new Chatbot();
        chatbot.chatbotId = chatbotId;
        chatbot.name = name;
        chatbot.description = description;
        chatbot.personaPrompt = personaPrompt;
        chatbot.model = model;
        chatbot.profileImage = profileImage;
        chatbot.isActive = true;
        chatbot.createdAt = LocalDateTime.now();
        return chatbot;
    }

    public void updatePersona(String personaPrompt, String model) {
        this.personaPrompt = personaPrompt;
        this.model = model;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
