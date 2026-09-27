package com.raon.tikitaka.application.chat;

/**
 * 챗봇이 지금 답할 수 있는지. 노트북이 꺼지면 available이 false가 된다.
 *
 * @param detail 사람이 읽는 사유. 정상일 땐 null
 */
public record ChatHealth(boolean available, String detail) {
}
