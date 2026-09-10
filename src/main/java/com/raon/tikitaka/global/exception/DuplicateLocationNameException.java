package com.raon.tikitaka.global.exception;

public class DuplicateLocationNameException extends RuntimeException {

    public DuplicateLocationNameException(String locationName) {
        super("이미 등록된 지역입니다: " + locationName);
    }
}
