package com.raon.tikitaka.adapter.equipment.dto;

import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.enums.ProductType;
import com.raon.tikitaka.domain.product.Product;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record EquipmentResponse(
        EquipmentItemResponse body,
        EquipmentItemResponse accessory,
        EquipmentItemResponse clothing,
        EquipmentItemResponse eyes,
        EquipmentItemResponse hairFront,
        EquipmentItemResponse hairBack,
        EquipmentItemResponse mouth,
        String hairColor,
        String eyesColor
) {

    public static EquipmentResponse from(EquippedItems equippedItems) {
        List<Product> products = equippedItems.products();
        Map<ColorGroup, Color> colors = equippedItems.colors();

        Map<ProductType, Product> byType = products.stream()
                .collect(Collectors.toMap(Product::getProductType, product -> product, (a, b) -> a));

        // 상품 미장착 상태에서도 유저가 골라둔 색은 항상 볼 수 있도록 최상위 필드로도 내려준다.
        // (아래 hairFront/hairBack/eyes에 얹히는 색은 그 부위가 실제로 장착돼 있어야만 나온다)
        Color hairColor = colors.get(ColorGroup.HAIR);
        Color eyesColor = colors.get(ColorGroup.EYES);

        return new EquipmentResponse(
                toResponse(byType.get(ProductType.BODY), null),
                toResponse(byType.get(ProductType.ACCESSORY), null),
                toResponse(byType.get(ProductType.CLOTHING), null),
                toResponse(byType.get(ProductType.EYES), eyesColor),
                toResponse(byType.get(ProductType.HAIR_FRONT), hairColor),
                toResponse(byType.get(ProductType.HAIR_BACK), hairColor),
                toResponse(byType.get(ProductType.MOUTH), null),
                hairColor != null ? hairColor.getColorCode() : null,
                eyesColor != null ? eyesColor.getColorCode() : null
        );
    }

    private static EquipmentItemResponse toResponse(Product product, Color color) {
        return product != null ? EquipmentItemResponse.from(product, color) : null;
    }
}
