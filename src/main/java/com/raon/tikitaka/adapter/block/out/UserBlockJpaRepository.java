package com.raon.tikitaka.adapter.block.out;

import com.raon.tikitaka.domain.block.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserBlockJpaRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlocker_UserIdAndBlocked_UserId(UUID blockerId, UUID blockedId);

    @Modifying
    @Query("delete from UserBlock b where b.blocker.userId = :blockerId and b.blocked.userId = :blockedId")
    void deleteByBlockerAndBlocked(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);

    @Query("""
            select b from UserBlock b
            join fetch b.blocked u
            where b.blocker.userId = :blockerId
              and u.status <> com.raon.tikitaka.domain.enums.UserStatus.WITHDRAWN
            order by b.createdAt desc, b.id desc
            """)
    List<UserBlock> findAllByBlocker(@Param("blockerId") UUID blockerId);

    @Modifying
    @Query("delete from UserBlock b where b.blocker.userId = :userId or b.blocked.userId = :userId")
    void deleteAllInvolving(@Param("userId") UUID userId);
}
