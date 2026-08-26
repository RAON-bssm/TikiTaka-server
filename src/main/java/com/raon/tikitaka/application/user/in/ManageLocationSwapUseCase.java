package com.raon.tikitaka.application.user.in;

import java.util.UUID;

/**
 * 유저의 지역 스위칭 기능. 서브 동네 설정과 예약, 예약 취소를 담당한다.
 * 실제 교환은 라운드 시작 직후 배치가 수행한다.
 */
public interface ManageLocationSwapUseCase {

    /**
     * 서브 동네 설정과 변경. 메인과 같은 동네는 400으로 거절한다.
     */
    void setSubLocation(UUID userId, Long locationId);

    /**
     * 스위칭 예약. 다음 라운드 시작 시 메인과 서브를 교환한다. 서브 동네가 없으면 400.
     */
    void requestLocationSwap(UUID userId);

    /**
     * 예약 취소.
     */
    void cancelLocationSwap(UUID userId);
}
