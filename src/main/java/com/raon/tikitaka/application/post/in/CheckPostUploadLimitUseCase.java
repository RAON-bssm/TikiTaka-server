package com.raon.tikitaka.application.post.in;

import java.util.UUID;

public interface CheckPostUploadLimitUseCase {

    /**
     * 사용자가 지금 글을 올릴 수 있는지 본다. 한도를 넘으면 429(ResponseStatusException)를 던진다.
     * 한도가 꺼져 있으면 아무것도 하지 않는다. S3 업로드 전에, 작성자 키(post-create) 안에서 불러야
     * 같은 사용자의 동시 요청이 한도를 함께 통과하지 않는다.
     */
    void checkUploadLimit(UUID userId);

    /**
     * 비동기 심사 대기열이 가득 찼는지 본다. 가득 찼으면 503(ResponseStatusException)을 던진다.
     * 사용자별이 아닌 전체 대기열 기준이라 작성자 키 밖에서 세므로 동시 요청이 조금 넘길 수 있는 느슨한 한도다.
     * S3 업로드 전에 불러야 거절할 요청에 업로드 비용을 쓰지 않는다. 한도가 0이면 아무것도 하지 않는다.
     */
    void checkQueueCapacity();
}
