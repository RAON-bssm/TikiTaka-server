package com.raon.tikitaka.adapter.push.dto;

/** platform: "ios" 또는 "android"(대소문자 무시). 해제(DELETE) 때는 token만 쓴다 */
public record PushTokenRequest(String token, String platform) {
}
