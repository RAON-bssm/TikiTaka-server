package com.raon.tikitaka.adapter.report.out;

import com.raon.tikitaka.adapter.post.out.PostJpaRepository;
import com.raon.tikitaka.adapter.user.out.UserJpaRepository;
import com.raon.tikitaka.application.report.out.ReportRepositoryPort;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.report.Report;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ReportPersistenceAdapter implements ReportRepositoryPort {

    private static final String UNIQUE_CONSTRAINT_PREFIX = "uk_report_";

    private final ReportJpaRepository reportJpaRepository;
    private final PostJpaRepository postJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<Post> findActivePost(UUID postId) {
        return postJpaRepository.findActiveById(postId);
    }

    @Override
    public Optional<Users> findUser(UUID userId) {
        return userJpaRepository.findById(userId);
    }

    @Override
    public boolean existsPostReport(UUID reporterId, UUID postId) {
        return reportJpaRepository.existsByReporter_UserIdAndPost_PostId(reporterId, postId);
    }

    @Override
    public boolean existsUserReport(UUID reporterId, UUID reportedUserId) {
        return reportJpaRepository.existsByReporter_UserIdAndReportedUser_UserId(reporterId, reportedUserId);
    }

    /**
     * 서비스의 존재 확인을 동시 요청 둘이 같이 통과하면 늦은 쪽이 유니크 제약에 걸린다.
     * 커밋 시점까지 미루면 DataIntegrityViolationException이 500으로 나가므로 여기서 flush해
     * 신고 유니크 제약 위반만 409로 바꾼다. 전역 핸들러는 건드리지 않는다.
     */
    @Override
    public Report save(Report report) {
        try {
            return reportJpaRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            String message = String.valueOf(e.getMostSpecificCause().getMessage());
            if (message.contains(UNIQUE_CONSTRAINT_PREFIX)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 대상입니다.");
            }
            throw e;
        }
    }
}
