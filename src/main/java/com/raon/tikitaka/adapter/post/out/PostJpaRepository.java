package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.domain.post.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostJpaRepository extends JpaRepository<Post, UUID> {

    @Query("""
            select p from Post p
            join fetch p.userId
            where p.board.boardId = :boardId and p.isActive = true
            order by p.createdAt desc
            """)
    List<Post> findAllActiveByBoardId(@Param("boardId") Long boardId);

    @Query("""
            select p from Post p
            join fetch p.userId
            where p.postId = :postId and p.isActive = true
            """)
    Optional<Post> findActiveById(@Param("postId") UUID postId);
}
