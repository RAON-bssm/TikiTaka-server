package com.raon.tikitaka.application.ranking.out;

import com.raon.tikitaka.domain.ranking.SeasonLocationResult;

public interface SeasonResultRepositoryPort {

    /**
     * 이 시즌의 확정 결과가 이미 있는지 확인한다. 있으면 다시 확정하지 않는다.
     */
    boolean existsBySeason(int season);

    SeasonLocationResult save(SeasonLocationResult result);
}
