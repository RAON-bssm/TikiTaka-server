package com.raon.tikitaka.adapter.product;

import com.raon.tikitaka.adapter.product.dto.ProductListResponse;
import com.raon.tikitaka.application.product.in.GetProductListUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final GetProductListUseCase getProductListUseCase;

    @GetMapping("/{user_id}")
    public ApiResponse<ProductListResponse> getProducts(@PathVariable("user_id") UUID userId) {
        ProductListResponse response = ProductListResponse.from(getProductListUseCase.getProducts(userId));
        return ApiResponse.of(200, "상품 목록 조회 성공", response);
    }
}
