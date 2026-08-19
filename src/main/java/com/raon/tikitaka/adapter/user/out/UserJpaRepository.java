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
            left join fetch u.subLocation
            where u.userId = :userId
            """)
    Optional<Users> findByIdWithLocations(UUID userId);

    /**
     * 네이티브로 FK 컬럼을 직접 읽는다 — JPQL로 u.subLocation.locationId를 참조하면
     * 암묵적 inner join이 생겨 subLocation이 null인 유저가 결과에서 빠지는 함정이 있다.
     */
    @Query(value = """
            select case when pending_location_swap then sub_location_id else main_location_id end
              from users
             where status = 'ACTIVE'
            """, nativeQuery = true)
    List<Long> findExpectedLocationIdsOfActiveUsers();

    List<Users> findAllByPendingLocationSwapTrue();

    List<Users> findAllByLastActiveAtBeforeAndStatus(LocalDateTime threshold, UserStatus status);
}
