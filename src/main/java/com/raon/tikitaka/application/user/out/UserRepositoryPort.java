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
     * ACTIVE 유저 전원의 "예정 소속" 지역 ID 목록 (유저 1명당 1건).
     * 예정 소속 = 스위칭 예약(pendingLocationSwap)이 있으면 subLocation, 없으면 mainLocation.
     * 매칭(7단계)이 라운드 시작 전날 인원을 셀 때, 다음 라운드 시작 직후 적용될 스위칭을 미리 반영하기 위한 것.
     */
    List<Long> findExpectedLocationIdsOfActiveUsers();

    /**
     * 지역 스위칭이 예약된 유저 전원 — 라운드 시작 직후 배치가 main ↔ sub를 교환할 대상.
     */
    List<Users> findAllWithPendingLocationSwap();

    /**
     * threshold 이전이 마지막 활동인 ACTIVE 유저 — 휴면 전환 배치의 대상.
     */
    List<Users> findAllActiveLastActiveBefore(LocalDateTime threshold);
}
