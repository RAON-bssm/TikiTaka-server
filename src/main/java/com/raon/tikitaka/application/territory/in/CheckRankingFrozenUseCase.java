package com.raon.tikitaka.application.territory.in;

import java.time.LocalDateTime;

public interface CheckRankingFrozenUseCase {
    /** 그 라운드에 at 시각 기준으로 개인·동네 랭킹이 동결됐는지. 가산은 글 작성 시각, 차감은 현재 시각을 넘긴다 */
    boolean isRankingFrozen(Long stageId, LocalDateTime at);
}
