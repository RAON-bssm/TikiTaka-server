package com.raon.tikitaka.application.gashapon;

import com.raon.tikitaka.domain.product.Product;

/**
 * 가샤폰 상품과 1회 구매당 뽑는 횟수.
 * 뽑는 횟수는 Product 테이블이 아니라 GashaponService에 정의돼 있어서 함께 묶어 내보낸다.
 */
public record GashaponProduct(Product product, int drawCount) {
}
