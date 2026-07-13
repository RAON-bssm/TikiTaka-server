package com.raon.tikitaka.adapter.equipment;

import com.raon.tikitaka.adapter.equipment.dto.EquipRequest;
import com.raon.tikitaka.adapter.equipment.dto.EquipmentResponse;
import com.raon.tikitaka.application.equipment.in.EquipItemUseCase;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipItemUseCase equipItemUseCase;
    private final GetEquippedItemsUseCase getEquippedItemsUseCase;

    @GetMapping("/{user_id}")
    public ApiResponse<EquipmentResponse> getEquippedItems(@PathVariable("user_id") UUID userId) {
        EquipmentResponse response = EquipmentResponse.from(getEquippedItemsUseCase.getEquippedItems(userId));
        return ApiResponse.of(200, "장비 조회 성공", response);
    }

    @PatchMapping("/{user_id}")
    public ApiResponse<Void> equip(@PathVariable("user_id") UUID userId, @RequestBody EquipRequest request) {
        equipItemUseCase.equip(userId, request.equipmentIds());
        return ApiResponse.of(204, "장비 착용 성공", null);
    }
}
