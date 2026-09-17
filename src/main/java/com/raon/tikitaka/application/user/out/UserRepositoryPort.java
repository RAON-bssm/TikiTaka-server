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
     * 변경 예약이 있으면 예약 지역, 없으면 현재 소속 지역이 예정 소속이다.
     * 매칭이 라운드 시작 전날 인원을 셀 때 곧 적용될 변경을 미리 반영하기 위한 것이다.
     */
    List<Long> findExpectedLocationIdsOfActiveUsers();

    /**
     * 지역 변경이 예약된 유저 전원. 라운드 시작 직후 배치가 옮길 대상이다.
     */
    List<Users> findAllWithPendingLocationChange();

    /**
     * 마지막 활동이 threshold 이전인 ACTIVE 유저. 휴면 전환 배치의 대상이다.
     */
    List<Users> findAllActiveLastActiveBefore(LocalDateTime threshold);
}
