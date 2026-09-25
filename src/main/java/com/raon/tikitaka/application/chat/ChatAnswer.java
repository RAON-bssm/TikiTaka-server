package com.raon.tikitaka.application.chat;

import java.util.List;

/**
 * AI 서버가 돌려준 답변.
 */
public record ChatAnswer(String reply, List<ChatSource> sources) {
}
