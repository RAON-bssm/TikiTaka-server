package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.post.Post;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

/** 대표 선발에서 게시물을 읽기만 한다. 기존 게시물 리포지토리는 건드리지 않는다 */
public interface TerritoryPostJpaRepository extends Repository<Post, UUID> {

    @Query("""
            select count(p) from Post p
             where p.board.match.stage.stageId = :stageId
               and p.status = com.raon.tikitaka.domain.post.PostStatus.PENDING
               and p.isActive = true
               and p.createdAt < :writtenBefore
            """)
    long countPendingReviews(@Param("stageId") Long stageId, @Param("writtenBefore") LocalDateTime writtenBefore);
}
