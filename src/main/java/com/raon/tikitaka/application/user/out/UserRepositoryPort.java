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

    Optional<Users> findByIdWithLocation(UUID userId);

    /**
     * ACTIVE 유저 전원의 소속 지역 ID 목록. 유저 1명당 1건이다.
     */
    List<Long> findMainLocationIdsOfActiveUsers();

    /**
     * 마지막 활동이 threshold 이전인 ACTIVE 유저. 휴면 전환 배치의 대상이다.
     */
    List<Users> findAllActiveLastActiveBefore(LocalDateTime threshold);
}
