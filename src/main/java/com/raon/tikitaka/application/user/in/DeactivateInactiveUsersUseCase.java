package com.raon.tikitaka.application.user.in;

public interface DeactivateInactiveUsersUseCase {

    /**
     * lastActiveAt이 dormantDays 이상 지난 ACTIVE 유저를 DORMANT로 전환한다.
     * 복귀는 로그인과 게시물 작성 시 호출되는 touch()가 담당한다.
     */
    void execute();
}
