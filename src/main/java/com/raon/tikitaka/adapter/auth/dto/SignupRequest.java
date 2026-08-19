package com.raon.tikitaka.adapter.auth.dto;

/**
 * mainLocationId는 가입 시 필수다. users.main_location이 NOT NULL이고
 * 매칭과 팀 점수, 게시판 필터가 모두 소속 있는 유저를 전제로 동작한다.
 */
public record SignupRequest(String signupToken, String userName, Long mainLocationId) {
}
