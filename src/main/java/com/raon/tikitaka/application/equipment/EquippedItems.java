package com.raon.tikitaka.application.equipment;

import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.product.Product;

import java.util.List;
import java.util.Map;

/**
 * 유저가 현재 장착 중인 상품 목록 + 파츠 그룹별 색상.
 * 색상이 없는 그룹은 colors 맵에 키 자체가 없다.
 */
public record EquippedItems(List<Product> products, Map<ColorGroup, Color> colors) {
}
