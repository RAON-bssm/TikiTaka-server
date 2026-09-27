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
            join fetch p.teamLocation
            where p.board.boardId = :boardId and p.isActive = true
            order by p.createdAt desc
            """)
    List<Post> findAllActiveByBoardId(@Param("boardId") Long boardId);

    @Query("""
            select p from Post p
            join fetch p.userId
            join fetch p.teamLocation
            where p.postId = :postId and p.isActive = true
            """)
    Optional<Post> findActiveById(@Param("postId") UUID postId);

    /**
     * 마이페이지 "게시물 보관함"용. 여러 라운드·게시판을 넘나드는 목록이라
     * board까지 그래프째 즉시 로딩해서 응답에 게시판·미션 정보를 같이 담는다.
     */
    @Query("""
            select p from Post p
            join fetch p.teamLocation
            join fetch p.board b
            join fetch b.match m
            join fetch m.team1
            join fetch m.team2
            join fetch m.stage
            where p.userId.userId = :userId and p.isActive = true
            order by p.createdAt desc
            """)
    List<Post> findAllActiveByUserId(@Param("userId") UUID userId);
}
