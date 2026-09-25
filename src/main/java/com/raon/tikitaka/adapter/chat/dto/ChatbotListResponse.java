package com.raon.tikitaka.adapter.chat.dto;

import com.raon.tikitaka.application.chat.ChatbotSummary;

import java.util.ArrayList;
import java.util.List;

public record ChatbotListResponse(List<ChatbotResponse> chatbot) {

    public static ChatbotListResponse from(List<ChatbotSummary> summaries) {
        List<ChatbotResponse> chatbot = new ArrayList<>();
        for (ChatbotSummary summary : summaries) {
            chatbot.add(ChatbotResponse.from(summary));
        }
        return new ChatbotListResponse(chatbot);
    }
}
