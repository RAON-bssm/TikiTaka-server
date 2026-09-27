package com.raon.tikitaka.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 채팅 메시지를 누가 썼는지. AI 서버로 이력을 보낼 때 role 값으로도 쓰인다.
 */
@Getter
@RequiredArgsConstructor
public enum ChatRole {

    USER("유저"),
    ASSISTANT("챗봇");

    private final String description;
}
