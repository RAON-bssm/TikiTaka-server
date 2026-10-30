package com.raon.tikitaka.application.territory.out;

import java.util.List;

/** 난수. 테스트에서 고정값으로 바꿔 끼운다 */
public interface RandomPort {

    /** [0, 1) */
    double nextDouble();

    <T> void shuffle(List<T> list);
}
