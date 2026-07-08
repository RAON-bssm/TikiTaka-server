package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService implements GetPostListUseCase, GetPostDetailUseCase, CreatePostUseCase, UpdatePostUseCase, DeletePostUseCase {

    private final PostRepositoryPort postRepositoryPort;

    @Override
    public List<Post> getPosts(Long boardId) {
        return postRepositoryPort.findAllActiveByBoardId(boardId);
    }

    @Override
    public Post getPost(UUID postId) {
        return postRepositoryPort.findActiveById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시물을 찾을 수 없습니다."));
    }

    @Override
    @Transactional
    public void createPost(UUID authorId, Long boardId, String content, String postImage) {
        Users author = postRepositoryPort.getUser(authorId);
        Board board = postRepositoryPort.getBoard(boardId);
        postRepositoryPort.save(Post.create(author, board, content, postImage));
    }

    @Override
    @Transactional
    public void updatePost(UUID postId, UUID requesterId, boolean isAdmin, String content) {
        Post post = getPost(postId);
        validateOwner(post, requesterId, isAdmin);
        post.updateContent(content);
    }

    @Override
    @Transactional
    public void deletePost(UUID postId, UUID requesterId, boolean isAdmin) {
        Post post = getPost(postId);
        validateOwner(post, requesterId, isAdmin);
        post.deactivate();
    }

    private void validateOwner(Post post, UUID requesterId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (!post.getUserId().getUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 게시물만 수정/삭제할 수 있습니다.");
        }
    }
}
