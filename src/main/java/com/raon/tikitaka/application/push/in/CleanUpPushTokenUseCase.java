package com.raon.tikitaka.application.push.in;

/** 오랫동안 등록되지 않은 토큰을 지운다(앱을 안 쓰거나 지운 기기) */
public interface CleanUpPushTokenUseCase {

    void cleanUpStaleTokens();
}
