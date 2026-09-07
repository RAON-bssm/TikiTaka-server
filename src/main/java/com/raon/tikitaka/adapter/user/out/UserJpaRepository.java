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
     * 네이티브로 FK 컬럼을 직접 읽는다. JPQL로 u.subLocation.locationId를 참조하면
     * 암묵적 inner join이 생겨 subLocation이 null인 유저가 결과에서 빠진다.
     */
    @Query(value = """
            select case when pending_location_swap then sub_location_id else main_location_id end
              from users
             where status = 'ACTIVE'
            """, nativeQuery = true)
    List<Long> findExpectedLocationIdsOfActiveUsers();

    /**
     * 라운드 팀 규모(n) 집계용. 메인 동네 1건에 더해, 서브 동네가 설정돼 있으면
     * 그 동네도 1건 추가로 센다 — 즉시 스위칭으로 들어올 수 있는 인원을 미리
     * 반영해서, 라운드 중간에 스위칭해 들어와도 분모(n)가 실제보다 작게
     * 잡혀 점수가 부풀려지는 걸 막기 위함이다.
     */
    @Query(value = """
            select main_location_id from users where status = 'ACTIVE'
            union all
            select sub_location_id from users where status = 'ACTIVE' and sub_location_id is not null
            """, nativeQuery = true)
    List<Long> findLocationIdsForMemberCount();

    List<Users> findAllByPendingLocationSwapTrue();

    List<Users> findAllByLastActiveAtBeforeAndStatus(LocalDateTime threshold, UserStatus status);
}
