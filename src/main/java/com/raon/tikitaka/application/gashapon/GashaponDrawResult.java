package com.raon.tikitaka.application.gashapon;

import com.raon.tikitaka.domain.product.Product;

public record GashaponDrawResult(Product product, boolean duplicate) {
}
