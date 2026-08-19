package com.raon.tikitaka.application.user.in;

public interface ApplyLocationSwapUseCase {

    /**
     * 지역 스위칭 예약(pendingLocationSwap)을 현재 라운드에 적용한다 (main ↔ sub 교환).
     * 라운드당 1회만 — Stage.swapApplied로 멱등을 보장한다.
     */
    void execute();
}
