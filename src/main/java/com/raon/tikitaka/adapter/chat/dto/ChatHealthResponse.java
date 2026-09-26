package com.raon.tikitaka.adapter.chat.dto;

import com.raon.tikitaka.application.chat.ChatHealth;

public record ChatHealthResponse(boolean available, String detail) {

    public static ChatHealthResponse from(ChatHealth health) {
        return new ChatHealthResponse(health.available(), health.detail());
    }
}
