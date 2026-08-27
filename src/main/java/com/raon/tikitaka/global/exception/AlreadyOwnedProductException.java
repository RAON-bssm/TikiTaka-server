package com.raon.tikitaka.global.exception;

public class AlreadyOwnedProductException extends RuntimeException {

    public AlreadyOwnedProductException(Long productId) {
        super("이미 보유한 상품입니다: " + productId);
    }
}
