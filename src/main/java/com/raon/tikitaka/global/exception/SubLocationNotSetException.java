package com.raon.tikitaka.global.exception;

public class SubLocationNotSetException extends RuntimeException {

    public SubLocationNotSetException() {
        super("두 번째 지역이 설정되지 않아 지역 스위칭을 신청할 수 없습니다.");
    }
}
