package com.raon.tikitaka.application.territory.in;

/** 그날 일급을 받은 유저에게 알림을 보낸다. 지급(05:57)은 새벽이라 알림은 08:00에 따로 보낸다 */
public interface NotifyDailyWageUseCase {

    void notifyTodayWage();
}
