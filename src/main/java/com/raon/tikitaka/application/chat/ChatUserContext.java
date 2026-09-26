package com.raon.tikitaka.application.chat;

import java.util.UUID;

/**
 * AI 서버에 헤더로 넘기는 유저 맥락. 전부 JWT로 검증된 값이라 클라이언트가 위조할 수 없다.
 *
 * @param atHome 본진에 있는가. 원정 중에는 지역 점수가 쌓이지 않아 챗봇이 안내할 수 있다
 */
public record ChatUserContext(
        UUID userId,
        String userName,
        Long mainLocationId,
        String mainLocation,
        Long currentLocationId,
        String currentLocation,
        boolean atHome
) {
}
