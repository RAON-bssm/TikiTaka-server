package com.raon.tikitaka.application.user.in;

public interface DeactivateInactiveUsersUseCase {

    /**
     * lastActiveAt이 dormantDays(7일) 이상 지난 ACTIVE 유저를 DORMANT로 전환한다.
     * 복귀는 touch()가 담당한다 (로그인·게시물 작성 시).
     * ⚠️ 로그인 브랜치 병합 전에는 스케줄에 올리지 않는다 — 로그인이 touch()를 못 하면 전원이 휴면된다.
     */
    void execute();
}
