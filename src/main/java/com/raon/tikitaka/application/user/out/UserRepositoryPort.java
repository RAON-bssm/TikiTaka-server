package com.raon.tikitaka.application.user.out;

import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.user.Users;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    // ===== 인증 (TK-70) =====

    Optional<Users> findById(UUID userId);

    Optional<Users> findByProviderAndProviderId(LoginProvider provider, String providerId);

    boolean existsByUserName(String userName);

    Users save(Users user);

    // ===== 시즌/랭킹 자동화 =====

    Optional<Users> findByIdWithLocations(UUID userId);

    /**
     * ACTIVE 유저 전원의 예정 소속 지역 ID 목록. 유저 1명당 1건이다.
     * 스위칭 예약이 있으면 subLocation, 없으면 mainLocation이 예정 소속이다.
     * 매칭이 라운드 시작 전날 인원을 셀 때 곧 적용될 스위칭을 미리 반영하기 위한 것이다.
     */
    List<Long> findExpectedLocationIdsOfActiveUsers();

    /**
     * 라운드 팀 규모(n) 집계용. 메인 동네 소속자 + 서브 동네 설정자를 합쳐서
     * 동네별로 센다. 유저 1명이 서브 동네를 설정해뒀으면 2건(메인 1 + 서브 1)이다.
     */
    List<Long> findLocationIdsForMemberCount();

    /**
     * 지역 스위칭이 예약된 유저 전원. 라운드 시작 직후 배치가 교환할 대상이다.
     */
    List<Users> findAllWithPendingLocationSwap();

    /**
     * 마지막 활동이 threshold 이전인 ACTIVE 유저. 휴면 전환 배치의 대상이다.
     */
    List<Users> findAllActiveLastActiveBefore(LocalDateTime threshold);
}
