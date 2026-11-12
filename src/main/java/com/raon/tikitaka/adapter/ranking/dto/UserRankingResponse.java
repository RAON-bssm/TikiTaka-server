package com.raon.tikitaka.adapter.ranking.dto;

import com.raon.tikitaka.adapter.equipment.dto.EquipmentResponse;
import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.ranking.UserRankRow;

import java.util.UUID;

/**
 * 개인 랭킹 응답 한 건. 필드는 API 명세서를 따른다.
 * user_equipment는 명세서에 없는 추가 필드로 GET /api/equipment/{userId}와 같은 모양의 착용 정보다.
 * 착용한 것이 없으면 모든 필드가 빠진 빈 객체다.
 */
public record UserRankingResponse(UUID userId, String userName, EquipmentResponse userEquipment,
                                  Integer userRank, Long userScore) {

    public static UserRankingResponse from(UserRankRow row, EquippedItems equippedItems) {
        return new UserRankingResponse(row.getUserId(), row.getUserName(), EquipmentResponse.from(equippedItems),
                row.getUserRank(), row.getUserScore());
    }
}
