package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.enums.ProductType;

import java.util.LinkedHashMap;
import java.util.Map;

public record EquipRequest(
        String body,
        String accessory,
        String clothing,
        String eyes,
        String hairFront,
        String hairBack,
        String mouth
) {

    public Map<ProductType, String> toSelections() {
        Map<ProductType, String> selections = new LinkedHashMap<>();
        putIfPresent(selections, ProductType.BODY, body);
        putIfPresent(selections, ProductType.ACCESSORY, accessory);
        putIfPresent(selections, ProductType.CLOTHING, clothing);
        putIfPresent(selections, ProductType.EYES, eyes);
        putIfPresent(selections, ProductType.HAIR_FRONT, hairFront);
        putIfPresent(selections, ProductType.HAIR_BACK, hairBack);
        putIfPresent(selections, ProductType.MOUTH, mouth);
        return selections;
    }

    private void putIfPresent(Map<ProductType, String> selections, ProductType type, String productId) {
        if (productId != null) {
            selections.put(type, productId);
        }
    }
}
