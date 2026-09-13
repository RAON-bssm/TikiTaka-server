package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<Users, UUID> {

    // ===== 인증 (TK-70) =====

    Optional<Users> findByProviderAndProviderId(LoginProvider provider, String providerId);

    boolean existsByUserName(String userName);

    // ===== 시즌/랭킹 자동화 =====

    @Query("""
            select u from Users u
            left join fetch u.mainLocation
            left join fetch u.pendingLocation
            where u.userId = :userId
            """)
    Optional<Users> findByIdWithLocations(UUID userId);

    /**
     * 네이티브로 FK 컬럼을 직접 읽는다. JPQL로 u.pendingLocation.locationId를 참조하면
     * 암묵적 inner join이 생겨 예약이 없는 유저가 결과에서 빠진다.
     */
    @Query(value = """
            select coalesce(pending_location_id, main_location_id)
              from users
             where status = 'ACTIVE'
            """, nativeQuery = true)
    List<Long> findExpectedLocationIdsOfActiveUsers();

    List<Users> findAllByPendingLocationIsNotNull();

    List<Users> findAllByLastActiveAtBeforeAndStatus(LocalDateTime threshold, UserStatus status);
}
