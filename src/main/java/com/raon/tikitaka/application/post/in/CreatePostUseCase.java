package com.raon.tikitaka.application.post.in;

import java.util.UUID;

/**
 * postThumbnail은 목록용 썸네일의 S3 key이고, 썸네일이 없으면 null이다.
 */
public interface CreatePostUseCase {

    /**
     * 동기 방식. AI 심사를 마친 점수로 DONE 글을 만들고 포인트와 점수를 바로 반영한다.
     */
    UUID createPost(UUID authorId, Long boardId, String content, String postImage, String postThumbnail,
                    Integer score, String aiReview);

    /**
     * 비동기 방식. 점수 없이 PENDING 글만 만든다. 포인트와 점수는 심사가 끝날 때(completeReview) 반영된다.
     */
    UUID createPending(UUID authorId, Long boardId, String content, String postImage, String postThumbnail);
}
