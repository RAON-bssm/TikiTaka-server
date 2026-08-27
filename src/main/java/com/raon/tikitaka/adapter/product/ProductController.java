package com.raon.tikitaka.adapter.product;

import com.raon.tikitaka.adapter.product.dto.ProductListResponse;
import com.raon.tikitaka.adapter.product.dto.PurchaseProductRequest;
import com.raon.tikitaka.application.product.in.GetProductListUseCase;
import com.raon.tikitaka.application.product.in.PurchaseProductUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final GetProductListUseCase getProductListUseCase;
    private final PurchaseProductUseCase purchaseProductUseCase;

    @GetMapping
    public ApiResponse<ProductListResponse> getProducts(@AuthenticationPrincipal UUID userId) {
        ProductListResponse response = ProductListResponse.from(getProductListUseCase.getProducts(userId));
        return ApiResponse.of(200, "상품 목록 조회 성공", response);
    }

    @PatchMapping("/store")
    public ApiResponse<Void> purchase(@AuthenticationPrincipal UUID userId, @RequestBody PurchaseProductRequest request) {
        purchaseProductUseCase.purchase(userId, request.productId());
        return ApiResponse.of(201, "상품 구매 성공", null);
    }
}
