package com.raon.tikitaka.application.chat;

/**
 * 답변의 근거가 된 동네 정보 한 건. 벡터DB를 붙이기 전까지는 AI 서버가 빈 목록을 준다.
 */
public record ChatSource(String type, String id, String title) {
}
