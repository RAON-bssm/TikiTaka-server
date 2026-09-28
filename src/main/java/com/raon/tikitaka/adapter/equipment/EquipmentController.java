package com.raon.tikitaka.adapter.equipment;

import com.raon.tikitaka.adapter.equipment.dto.EquipRequest;
import com.raon.tikitaka.adapter.equipment.dto.EquipmentResponse;
import com.raon.tikitaka.application.equipment.in.EquipItemUseCase;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipItemUseCase equipItemUseCase;
    private final GetEquippedItemsUseCase getEquippedItemsUseCase;

    @GetMapping
    public ApiResponse<EquipmentResponse> getEquippedItems(@AuthenticationPrincipal UUID userId) {
        EquipmentResponse response = EquipmentResponse.from(getEquippedItemsUseCase.getEquippedItems(userId));
        return ApiResponse.of(200, "장비 조회 성공", response);
    }

    /**
     * 랭킹 화면 등에서 다른 유저가 장착 중인 캐릭터 파츠(프로필 이미지 합성용)를 조회한다.
     * 인증 주체(@AuthenticationPrincipal)가 아니라 경로의 userId를 그대로 조회하므로
     * 본인 여부와 무관하게 어떤 유저의 장비든 볼 수 있다. 인증 자체는 SecurityConfig의
     * anyRequest().authenticated() 기본 정책을 그대로 따른다(로그인은 필요).
     */
    @GetMapping("/{userId}")
    public ApiResponse<EquipmentResponse> getEquippedItemsOf(@PathVariable UUID userId) {
        EquipmentResponse response = EquipmentResponse.from(getEquippedItemsUseCase.getEquippedItems(userId));
        return ApiResponse.of(200, "유저 장비 조회 성공", response);
    }

    @PatchMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> equip(@AuthenticationPrincipal UUID userId, @RequestBody EquipRequest request) {
        equipItemUseCase.equip(userId, request.toSelections());
        return ApiResponse.of(204, "장비 착용 성공", null);
    }
}
