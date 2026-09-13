package com.raon.tikitaka.application.user.in;

/**
 * 예약된 지역 변경 일괄 적용. 라운드 시작 직후 배치가 하루 한 번 실행한다.
 * 라운드당 1회만 적용되도록 Stage에 적용 여부를 기록해 멱등성을 지킨다.
 */
public interface ApplyLocationChangeUseCase {

    void execute();
}
