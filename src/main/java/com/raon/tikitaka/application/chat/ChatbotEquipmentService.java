package com.raon.tikitaka.application.chat;

import com.raon.tikitaka.application.chat.in.EquipChatbotUseCase;
import com.raon.tikitaka.application.chat.out.ChatbotEquipmentRepositoryPort;
import com.raon.tikitaka.application.chat.out.ChatbotEquippedColorRepositoryPort;
import com.raon.tikitaka.application.chat.out.ChatbotRepositoryPort;
import com.raon.tikitaka.application.color.out.ColorRepositoryPort;
import com.raon.tikitaka.application.product.out.ProductRepositoryPort;
import com.raon.tikitaka.domain.chat.Chatbot;
import com.raon.tikitaka.domain.chat.ChatbotEquipment;
import com.raon.tikitaka.domain.chat.ChatbotEquippedColor;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.product.Product;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 챗봇 장비 착용. 유저 EquipmentService와 같은 규칙(슬롯별 교체, delete-then-insert)을 따르되
 * 챗봇은 인벤토리가 없으므로 보유 검증 대신 활성 상품인지만 확인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChatbotEquipmentService implements EquipChatbotUseCase {

    private final ChatbotRepositoryPort chatbotRepositoryPort;
    private final ProductRepositoryPort productRepositoryPort;
    private final ColorRepositoryPort colorRepositoryPort;
    private final ChatbotEquipmentRepositoryPort chatbotEquipmentRepositoryPort;
    private final ChatbotEquippedColorRepositoryPort chatbotEquippedColorRepositoryPort;

    @Override
    public void equip(String chatbotId, Map<ProductType, String> productSelections, Map<ColorGroup, String> colorSelections) {
        if (productSelections.isEmpty() && colorSelections.isEmpty()) {
            return;
        }

        Chatbot chatbot = chatbotRepositoryPort.findActiveById(chatbotId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 챗봇입니다: " + chatbotId));

        if (!productSelections.isEmpty()) {
            equipProducts(chatbot, productSelections);
        }
        if (!colorSelections.isEmpty()) {
            equipColors(chatbot, colorSelections);
        }
    }

    private void equipProducts(Chatbot chatbot, Map<ProductType, String> selections) {
        // 검증을 모두 끝낸 뒤에 지워야 중간 실패 시 기존 착용이 사라지지 않는다(트랜잭션 롤백과 별개로 순서도 지킨다)
        List<ChatbotEquipment> newEquipments = selections.entrySet().stream()
                .map(entry -> ChatbotEquipment.of(chatbot, findProductOfType(entry.getKey(), entry.getValue())))
                .toList();

        chatbotEquipmentRepositoryPort.deleteAllByChatbotIdAndProductTypes(chatbot.getChatbotId(), selections.keySet());
        chatbotEquipmentRepositoryPort.saveAll(newEquipments);
    }

    private void equipColors(Chatbot chatbot, Map<ColorGroup, String> selections) {
        List<ChatbotEquippedColor> newColors = selections.entrySet().stream()
                .map(entry -> ChatbotEquippedColor.of(chatbot, entry.getKey(), findColorOfGroup(entry.getKey(), entry.getValue())))
                .toList();

        chatbotEquippedColorRepositoryPort.deleteAllByChatbotIdAndColorGroups(chatbot.getChatbotId(), selections.keySet());
        chatbotEquippedColorRepositoryPort.saveAll(newColors);
    }

    private Product findProductOfType(ProductType type, String productId) {
        Product product = productRepositoryPort.findActiveById(productId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다: " + productId));
        if (product.getProductType() != type) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 타입이 일치하지 않습니다: " + productId);
        }
        return product;
    }

    private Color findColorOfGroup(ColorGroup group, String colorCode) {
        return colorRepositoryPort.findByColorGroupAndColorCode(group, colorCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 색상입니다: " + colorCode));
    }
}
