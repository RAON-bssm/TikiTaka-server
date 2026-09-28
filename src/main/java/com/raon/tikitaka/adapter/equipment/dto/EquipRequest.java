package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.domain.enums.ColorGroup;
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
        String mouth,
        String hairColor,
        String eyesColor
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

    public Map<ColorGroup, String> toColorSelections() {
        Map<ColorGroup, String> selections = new LinkedHashMap<>();
        putIfPresent(selections, ColorGroup.HAIR, hairColor);
        putIfPresent(selections, ColorGroup.EYES, eyesColor);
        return selections;
    }

    private <T> void putIfPresent(Map<T, String> selections, T key, String value) {
        if (value != null) {
            selections.put(key, value);
        }
    }
}
