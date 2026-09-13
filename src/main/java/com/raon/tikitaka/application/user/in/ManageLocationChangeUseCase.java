package com.raon.tikitaka.application.user.in;

import java.util.UUID;

/**
 * 유저 본인의 지역 변경 예약 관리. 예약은 다음 라운드 시작 직후에 적용되고
 * 그 전까지는 언제든 다른 지역으로 바꾸거나 취소할 수 있다.
 * 예약 현황은 내 정보 조회(GET /api/user/me)의 pending_location으로 확인한다.
 */
public interface ManageLocationChangeUseCase {

    /**
     * 지역 변경 예약. 이미 예약이 있으면 새 지역으로 덮어쓴다.
     */
    void reserve(UUID userId, Long locationId);

    /**
     * 예약 취소. 예약이 없는 상태에서 호출해도 성공으로 처리한다.
     */
    void cancel(UUID userId);
}
