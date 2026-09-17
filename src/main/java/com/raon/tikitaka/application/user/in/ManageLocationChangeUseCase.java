package com.raon.tikitaka.application.user.in;

import java.util.UUID;

/**
 * 유저 본인의 지역 관리. 지역은 두 가지다.
 *
 * 본진(main)은 지역 점수가 쌓이는 곳이라 라운드 도중에는 바뀌지 않는다.
 * 변경은 예약으로만 받고 다음 라운드 시작 직후 배치가 적용한다.
 *
 * 현재 지역(current)은 지금 있는 동네라 언제든 즉시 옮길 수 있다.
 * 현재 지역의 게시판에만 글을 쓸 수 있고, 본진 밖에서 쓴 글은 개인 점수만 올린다.
 */
public interface ManageLocationChangeUseCase {

    /**
     * 본진 변경 예약. 이미 예약이 있으면 새 지역으로 덮어쓴다.
     */
    void reserve(UUID userId, Long locationId);

    /**
     * 본진 변경 예약 취소. 예약이 없는 상태에서 호출해도 성공으로 처리한다.
     */
    void cancel(UUID userId);

    /**
     * 현재 지역 이동. 즉시 반영되고 본진과 달라도 된다.
     */
    void moveCurrentLocation(UUID userId, Long locationId);
}
