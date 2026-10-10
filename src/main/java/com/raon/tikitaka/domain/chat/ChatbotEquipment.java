package com.raon.tikitaka.domain.chat;

import com.raon.tikitaka.domain.product.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챗봇이 착용 중인 파츠. 유저 Equipment와 같은 구조지만 인벤토리 보유 검증 없이 관리자가 지정한다.
 * 슬롯(ProductType)당 하나만 착용하며, 교체는 유저 장비와 같은 delete-then-insert 방식이다.
 * 슬롯 유일성은 product_type이 product 테이블에 있어 DB 제약으로 걸 수 없으므로 서비스가 보장한다.
 */
@Entity
@Table(
        name = "chatbot_equipment",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chatbot_equipment_chatbot_product", columnNames = {"chatbot_id", "product_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chatbot_equipment_id")
    private Long chatbotEquipmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private Chatbot chatbot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private ChatbotEquipment(Chatbot chatbot, Product product) {
        this.chatbot = chatbot;
        this.product = product;
    }

    public static ChatbotEquipment of(Chatbot chatbot, Product product) {
        return new ChatbotEquipment(chatbot, product);
    }
}
