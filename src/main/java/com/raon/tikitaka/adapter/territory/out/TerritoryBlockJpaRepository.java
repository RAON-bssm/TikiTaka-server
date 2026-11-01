package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.block.UserBlock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** 땅 조회용 차단 목록. 유저 엔티티를 읽지 않고 id만 가져온다 */
public interface TerritoryBlockJpaRepository extends Repository<UserBlock, Long> {

    @Query("select b.blocked.userId from UserBlock b where b.blocker.userId = :blockerId")
    List<UUID> findBlockedUserIds(@Param("blockerId") UUID blockerId);
}
