package com.raon.tikitaka.domain.chat;

import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챗봇의 파츠 그룹별 색상. 챗봇당 그룹(HAIR/EYES)별로 한 행만 존재한다(유니크 제약).
 * 유저 EquippedColor와 마찬가지로 delete-then-insert 방식으로 교체한다.
 */
@Entity
@Table(
        name = "chatbot_equipped_color",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chatbot_equipped_color_chatbot_group", columnNames = {"chatbot_id", "color_group"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotEquippedColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chatbot_equipped_color_id")
    private Long chatbotEquippedColorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_group", nullable = false)
    private ColorGroup colorGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    private ChatbotEquippedColor(Chatbot chatbot, ColorGroup colorGroup, Color color) {
        this.chatbot = chatbot;
        this.colorGroup = colorGroup;
        this.color = color;
    }

    public static ChatbotEquippedColor of(Chatbot chatbot, ColorGroup colorGroup, Color color) {
        return new ChatbotEquippedColor(chatbot, colorGroup, color);
    }
}
