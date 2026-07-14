package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.enums.ProductType;

import java.util.LinkedHashMap;
import java.util.Map;

public record EquipRequest(
        Long body,
        Long accessory,
        Long clothing,
        Long eyes,
        Long hairFront,
        Long hairBack,
        Long mouth
) {

    public Map<ProductType, Long> toSelections() {
        Map<ProductType, Long> selections = new LinkedHashMap<>();
        putIfPresent(selections, ProductType.BODY, body);
        putIfPresent(selections, ProductType.ACCESSORY, accessory);
        putIfPresent(selections, ProductType.CLOTHING, clothing);
        putIfPresent(selections, ProductType.EYES, eyes);
        putIfPresent(selections, ProductType.HAIR_FRONT, hairFront);
        putIfPresent(selections, ProductType.HAIR_BACK, hairBack);
        putIfPresent(selections, ProductType.MOUTH, mouth);
        return selections;
    }

    private void putIfPresent(Map<ProductType, Long> selections, ProductType type, Long productId) {
        if (productId != null) {
            selections.put(type, productId);
        }
    }
}
