package com.raon.tikitaka.adapter.product.dto;

import com.raon.tikitaka.application.gashapon.GashaponDrawResult;
import com.raon.tikitaka.domain.product.Product;

public record GashaponDrawItemResponse(
        String productId,
        String productName,
        String productImage,
        String productType,
        boolean duplicate,
        String message
) {

    private static final String NEW_MESSAGE = "새로운 아이템을 뽑았습니다!";
    private static final String DUPLICATE_MESSAGE = "이미 보유중인 아이템을 뽑았습니다!!";

    public static GashaponDrawItemResponse from(GashaponDrawResult result) {
        Product product = result.product();
        boolean duplicate = result.duplicate();

        return new GashaponDrawItemResponse(
                product.getProductId(),
                product.getProductName(),
                product.getProductImage(),
                product.getProductType().name().toLowerCase(),
                duplicate,
                duplicate ? DUPLICATE_MESSAGE : NEW_MESSAGE
        );
    }
}
