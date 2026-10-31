package com.raon.tikitaka.application.territory.out;

import java.util.UUID;

/** 포인트 원자적 갱신. 호출하는 쪽 트랜잭션 안에서 실행된다 */
public interface TerritoryPointPort {

    int getPoint(UUID userId);

    /** 잔액이 충분하면 차감하고 true, 부족하면 아무것도 바꾸지 않고 false */
    boolean deduct(UUID userId, int amount);

    void add(UUID userId, int amount);
}
