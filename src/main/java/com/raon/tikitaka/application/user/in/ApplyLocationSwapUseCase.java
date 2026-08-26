package com.raon.tikitaka.application.user.in;

public interface ApplyLocationSwapUseCase {

    /**
     * 지역 스위칭 예약을 현재 라운드에 적용해 메인과 서브 동네를 교환한다.
     * Stage.swapApplied로 라운드당 1회만 실행되게 보장한다.
     */
    void execute();
}
