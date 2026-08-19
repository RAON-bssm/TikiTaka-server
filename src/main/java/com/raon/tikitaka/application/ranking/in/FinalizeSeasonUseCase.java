package com.raon.tikitaka.application.ranking.in;

public interface FinalizeSeasonUseCase {

    /**
     * 끝난 시즌 중 아직 확정되지 않은 시즌을 찾아 최종 순위를 박제한다.
     * 멱등 — 이미 확정된 시즌은 건너뛰므로 여러 번 호출해도 안전하다.
     */
    void execute();
}
