package com.raon.tikitaka.application.post.out;

import com.raon.tikitaka.application.post.PostLikeCountRow;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostLike;
import com.raon.tikitaka.domain.user.Users;

import java.util.List;
import java.util.UUID;

public interface PostLikeRepositoryPort {

    boolean existsByPostAndUser(Post post, Users user);

    void save(PostLike postLike);

    void deleteByPostAndUser(Post post, Users user);

    /**
     * postIds에 속한 게시물들의 좋아요 수 집계. 좋아요가 하나도 없는 게시물은
     * 결과에서 빠지므로 호출부가 0으로 채워야 한다.
     */
    List<PostLikeCountRow> countByPostIds(List<UUID> postIds);

    /**
     * postIds 중 userId가 좋아요를 누른 게시물 id 목록. 목록 조회에서 N+1 없이
     * likedByMe를 한 번에 채우기 위함이다.
     */
    List<UUID> findLikedPostIds(List<UUID> postIds, UUID userId);
}
