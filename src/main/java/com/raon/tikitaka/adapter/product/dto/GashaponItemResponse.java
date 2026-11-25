package com.raon.tikitaka.adapter.product.dto;

import com.raon.tikitaka.application.gashapon.GashaponProduct;
import com.raon.tikitaka.domain.product.Product;

public record GashaponItemResponse(
        String productId,
        String productName,
        Integer price,
        Integer drawCount,
        String productImage,
        String description
) {

    public static GashaponItemResponse from(GashaponProduct gashapon) {
        Product product = gashapon.product();
        return new GashaponItemResponse(
                product.getProductId(),
                product.getProductName(),
                product.getPrice(),
                gashapon.drawCount(),
                product.getProductImage(),
                product.getDescription()
        );
    }
}
