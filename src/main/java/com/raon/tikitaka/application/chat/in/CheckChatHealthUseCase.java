package com.raon.tikitaka.application.chat.in;

import com.raon.tikitaka.application.chat.ChatHealth;

/**
 * 챗봇이 지금 답할 수 있는 상태인지 확인한다.
 * 로컬 LLM이 노트북에서 돌기 때문에 꺼져 있는 경우가 흔하고, 앱이 미리 안내할 수 있어야 한다.
 */
public interface CheckChatHealthUseCase {

    ChatHealth check();
}
