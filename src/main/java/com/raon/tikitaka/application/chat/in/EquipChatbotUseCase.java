package com.raon.tikitaka.application.chat.in;

import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.enums.ProductType;

import java.util.Map;

/**
 * 관리자가 챗봇에게 파츠와 색을 착용시킨다. 넘긴 슬롯만 교체되고 생략한 슬롯은 유지된다.
 * 유저 착용과 달리 인벤토리 보유 여부는 보지 않는다.
 */
public interface EquipChatbotUseCase {

    void equip(String chatbotId, Map<ProductType, String> productSelections, Map<ColorGroup, String> colorSelections);
}
