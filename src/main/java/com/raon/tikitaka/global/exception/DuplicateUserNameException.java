package com.raon.tikitaka.global.exception;

public class DuplicateUserNameException extends RuntimeException {

    public DuplicateUserNameException(String userName) {
        super("이미 사용 중인 닉네임입니다: " + userName);
    }
}
