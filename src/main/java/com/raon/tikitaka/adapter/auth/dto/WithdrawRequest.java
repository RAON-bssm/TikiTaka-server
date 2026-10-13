package com.raon.tikitaka.adapter.auth.dto;

/**
 * Apple 유저만 authorizationCode가 필요하다(탈퇴 직전에 앱에서 Apple 재인증으로 받은 1회용 코드, 5분 유효).
 * 카카오/구글 유저는 바디 없이 호출해도 된다.
 */
public record WithdrawRequest(String authorizationCode) {
}
