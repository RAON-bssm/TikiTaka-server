package com.raon.tikitaka.application.chat;

import com.raon.tikitaka.domain.enums.ChatRole;

/**
 * AI 서버로 실어 보내는 지난 대화 한 줄.
 */
public record ChatTurn(ChatRole role, String content) {
}
