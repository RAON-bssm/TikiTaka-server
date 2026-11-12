package com.raon.tikitaka.application.equipment.in;

import com.raon.tikitaka.application.equipment.EquippedItems;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface GetEquippedItemsUseCase {

    EquippedItems getEquippedItems(UUID userId);

    /**
     * 여러 유저의 착용 정보를 한 번에 읽는다. 착용 상품과 색을 각각 한 번씩만 조회하므로 유저 수와 상관없이 쿼리는 두 번이다.
     * 요청한 모든 id가 결과에 들어가고, 착용한 것이 없는 유저(없는 유저 포함)는 빈 EquippedItems다.
     * 단건 조회와 달리 유저 존재 여부를 확인하지 않는다. 한 명 때문에 목록 전체가 실패하지 않게 하기 위해서다.
     */
    Map<UUID, EquippedItems> getEquippedItemsByUserIds(Collection<UUID> userIds);
}
