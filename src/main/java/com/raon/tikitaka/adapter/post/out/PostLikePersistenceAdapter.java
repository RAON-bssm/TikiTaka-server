package com.raon.tikitaka.adapter.post.out;

import com.raon.tikitaka.application.post.PostLikeCountRow;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostLike;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostLikePersistenceAdapter implements PostLikeRepositoryPort {

    private final PostLikeJpaRepository postLikeJpaRepository;

    @Override
    public boolean existsByPostAndUser(Post post, Users user) {
        return postLikeJpaRepository.existsByPostAndUser(post, user);
    }

    @Override
    public void save(PostLike postLike) {
        postLikeJpaRepository.save(postLike);
    }

    @Override
    public void deleteByPostAndUser(Post post, Users user) {
        postLikeJpaRepository.deleteByPostAndUser(post, user);
    }

    @Override
    public List<PostLikeCountRow> countByPostIds(List<UUID> postIds) {
        return postLikeJpaRepository.countByPostIds(postIds);
    }
}
