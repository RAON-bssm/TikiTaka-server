package com.raon.tikitaka.adapter.auth.dto;

/**
 * mainLocationId — 가입 시 필수. 소속 없는 유저는 존재하지 않는다(users.main_location NOT NULL,
 * 매칭·팀 점수·게시판 필터가 모두 이 전제 위에 서 있다).
 */
public record SignupRequest(String signupToken, String userName, Long mainLocationId) {
}
