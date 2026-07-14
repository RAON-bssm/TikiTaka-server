package com.raon.tikitaka.global.exception;

public class InsufficientPointException extends RuntimeException {

    public InsufficientPointException() {
        super("잔액 부족");
    }
}
