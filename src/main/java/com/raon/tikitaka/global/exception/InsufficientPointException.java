package com.raon.tikitaka.global.exception;

public class InsufficientPointException extends RuntimeException {

    public InsufficientPointException(int currentPoint, int requiredPoint) {
        super(String.format("포인트가 부족합니다. (보유: %d / 필요: %d)", currentPoint, requiredPoint));
    }
}
