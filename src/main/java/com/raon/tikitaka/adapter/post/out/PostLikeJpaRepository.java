package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.application.post.PostLikeCountRow;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostLike;
import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostLikeJpaRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPostAndUser(Post post, Users user);

    void deleteByPostAndUser(Post post, Users user);

    /**
     * 게시판 하나의 게시물 전체(또는 상세 조회 시 게시물 한 건)에 대해 좋아요 수를
     * 한 번에 집계한다. N+1을 피하기 위함이다. 좋아요가 하나도 없는 게시물은
     * 결과에서 빠지므로 호출부가 0으로 채워야 한다.
     */
    @Query(value = """
            select post_id as postId, count(*) as likeCount
              from post_like
             where post_id in (:postIds)
             group by post_id
            """, nativeQuery = true)
    List<PostLikeCountRow> countByPostIds(@Param("postIds") List<UUID> postIds);
}
