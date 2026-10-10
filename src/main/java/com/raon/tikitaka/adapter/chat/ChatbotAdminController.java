package com.raon.tikitaka.adapter.chat;

import com.raon.tikitaka.adapter.equipment.dto.EquipRequest;
import com.raon.tikitaka.application.chat.in.EquipChatbotUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 챗봇 관리 API. 경로가 /api/admin/** 이라 ADMIN 역할만 접근할 수 있다.
 * 장비 착용 바디는 유저 착용(PATCH /api/equipment)과 같은 형태라 EquipRequest를 그대로 쓴다.
 */
@RestController
@RequestMapping("/api/admin/chatbot")
@RequiredArgsConstructor
public class ChatbotAdminController {

    private final EquipChatbotUseCase equipChatbotUseCase;

    @PatchMapping("/{chatbotId}/equipment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> equip(@PathVariable String chatbotId, @RequestBody EquipRequest request) {
        equipChatbotUseCase.equip(chatbotId, request.toSelections(), request.toColorSelections());
        return ApiResponse.of(204, "챗봇 장비 착용 성공", null);
    }
}
