package com.raon.tikitaka.application.chat.in;

import java.util.UUID;

/**
 * 특정 챗봇과의 대화를 비운다.
 *
 * 이력을 계속 실어 보내는 구조라 한번 틀어진 대화가 계속 맥락으로 끌려다닌다.
 * 유저에게도 "새로 시작"이 필요하고 프롬프트를 고칠 때도 이게 없으면 매번 DB를 손으로 지워야 한다.
 */
public interface ClearChatUseCase {

    void clear(UUID userId, String chatbotId);
}
