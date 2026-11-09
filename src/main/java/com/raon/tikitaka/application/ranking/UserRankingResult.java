package com.raon.tikitaka.application.ranking;

import com.raon.tikitaka.application.equipment.EquippedItems;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 개인 랭킹 조회 결과. 상위 목록과 호출자 본인의 순위를 담는다.
 * myRanking은 본인이 이번 라운드에 게시물을 하나도 안 올렸으면 null이다.
 * equipments는 ranking과 myRanking에 나온 유저들의 착용 정보이고 유저 id로 찾는다.
 */
public record UserRankingResult(List<UserRankRow> ranking, UserRankRow myRanking,
                                Map<UUID, EquippedItems> equipments) {

    /**
     * 맵에 없는 유저(데이터 불일치 등)도 그 행만 기본 캐릭터로 내려가도록 빈 EquippedItems를 돌려준다.
     */
    public EquippedItems equipmentOf(UUID userId) {
        return equipments.getOrDefault(userId, EquippedItems.empty());
    }
}
