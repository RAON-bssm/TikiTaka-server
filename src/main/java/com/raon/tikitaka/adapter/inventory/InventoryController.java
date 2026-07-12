package com.raon.tikitaka.adapter.inventory;

import com.raon.tikitaka.adapter.inventory.dto.InventoryResponse;
import com.raon.tikitaka.application.inventory.UserInventory;
import com.raon.tikitaka.application.inventory.in.GetInventoryUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final GetInventoryUseCase getInventoryUseCase;

    @GetMapping("/{user_id}")
    public ApiResponse<InventoryResponse> getInventory(@PathVariable("user_id") UUID userId) {
        UserInventory userInventory = getInventoryUseCase.getInventory(userId);
        InventoryResponse response = InventoryResponse.from(userInventory);
        return ApiResponse.of(200, "인벤토리 조회 성공", response);
    }
}
