package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.Representative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RepresentativeJpaRepository extends JpaRepository<Representative, Long> {

    /**
     * 선발 결과 조회. 유저와 배정 동을 한 번에 읽는다(N+1 방지).
     * 개인 랭킹 조회와 같게 탈퇴 유저는 빼고, viewerId가 차단한 유저도 뺀다. 선발 이력 행은 그대로 둔다.
     */
    @Query("""
            select r from Representative r
              join fetch r.user u
              join fetch r.assignedDong d
             where r.cycle.cycleId = :cycleId
               and (:locationId is null or r.location.locationId = :locationId)
               and u.status <> com.raon.tikitaka.domain.enums.UserStatus.WITHDRAWN
               and not exists (select 1 from UserBlock b
                                where b.blocker.userId = :viewerId
                                  and b.blocked.userId = u.userId)
             order by r.location.locationId, r.rank
            """)
    List<Representative> findVisibleByCycle(@Param("cycleId") Long cycleId, @Param("locationId") Long locationId,
                                            @Param("viewerId") UUID viewerId);
}
