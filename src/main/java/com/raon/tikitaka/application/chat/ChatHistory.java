package com.raon.tikitaka.application.chat;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대화 목록 한 페이지. messages는 오래된 것부터 최신 순이다.
 *
 * @param nextBefore 다음 페이지를 요청할 때 before에 넣을 값. 더 없으면 null
 */
public record ChatHistory(List<ChatMessageView> messages, LocalDateTime nextBefore, boolean hasMore) {
}
