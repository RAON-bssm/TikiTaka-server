package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface CheckPostUploadLimitUseCase {

    /**
     * 사용자가 지금 글을 올릴 수 있는지 본다. 한도를 넘으면 429(ResponseStatusException)를 던진다.
     * 한도가 꺼져 있으면 아무것도 하지 않는다. S3 업로드 전에, 작성자 키(post-create) 안에서 불러야
     * 같은 사용자의 동시 요청이 한도를 함께 통과하지 않는다.
     */
    void checkUploadLimit(UUID userId);
}
