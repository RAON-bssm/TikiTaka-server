package com.raon.tikitaka.application.report.out;

import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.report.Report;
import com.raon.tikitaka.domain.user.Users;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepositoryPort {

    /**
     * 삭제되지 않은 게시물만 돌려준다. 작성자와 게시판까지 같이 읽는다.
     */
    Optional<Post> findActivePost(UUID postId);

    Optional<Users> findUser(UUID userId);

    boolean existsPostReport(UUID reporterId, UUID postId);

    boolean existsUserReport(UUID reporterId, UUID reportedUserId);

    /**
     * 즉시 flush해서 유니크 제약 위반을 이 호출 안에서 드러낸다.
     * 동시 요청으로 제약이 깨지면 500 대신 409로 바꿔 던진다.
     */
    Report save(Report report);
}
