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
            where u.userId = :userId
            """)
    Optional<Users> findByIdWithLocation(UUID userId);

    /**
     * ACTIVE 유저 전원의 소속 지역 ID 목록. 유저 1명당 1건이다.
     */
    @Query(value = """
            select main_location_id from users where status = 'ACTIVE'
            """, nativeQuery = true)
    List<Long> findMainLocationIdsOfActiveUsers();

    List<Users> findAllByLastActiveAtBeforeAndStatus(LocalDateTime threshold, UserStatus status);
}
