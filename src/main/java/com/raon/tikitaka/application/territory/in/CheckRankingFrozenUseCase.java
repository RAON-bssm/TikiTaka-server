package com.raon.tikitaka.application.territory.in;

import java.time.LocalDateTime;

public interface CheckRankingFrozenUseCase {
    /** 그 라운드에 지금 쓴 글이 개인·동네 랭킹에 반영되면 안 되는지 */
    boolean isRankingFrozen(Long stageId, LocalDateTime at);
}
