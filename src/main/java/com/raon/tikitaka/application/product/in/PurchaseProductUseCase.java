package com.raon.tikitaka.application.product.in;

import java.util.UUID;

public interface PurchaseProductUseCase {

    void purchase(UUID userId, Long productId);
}
