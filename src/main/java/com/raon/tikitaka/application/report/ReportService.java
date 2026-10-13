package com.raon.tikitaka.application.report;

import com.raon.tikitaka.application.report.ReportSubmittedEvent.TargetType;
import com.raon.tikitaka.application.report.in.ReportPostUseCase;
import com.raon.tikitaka.application.report.in.ReportUserUseCase;
import com.raon.tikitaka.application.report.out.ReportRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.ReportReason;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.report.Report;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService implements ReportPostUseCase, ReportUserUseCase {

    static final int DETAIL_MAX_LENGTH = 200;

    private final ReportRepositoryPort reportRepositoryPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Long reportPost(UUID reporterId, UUID postId, ReportReason reason, String detail) {
        String normalizedDetail = validate(reason, detail);
        Users reporter = getReporter(reporterId);
        Post post = reportRepositoryPort.findActivePost(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "게시물을 찾을 수 없습니다."));

        Users author = post.getUserId();
        if (author.getUserId().equals(reporterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본인이 올린 게시물은 신고할 수 없습니다.");
        }
        if (reportRepositoryPort.existsPostReport(reporterId, postId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 게시물입니다.");
        }

        Report report = reportRepositoryPort.save(Report.ofPost(reporter, post, reason, normalizedDetail));

        // 리스너는 커밋 후 다른 스레드에서 돌기 때문에 지연 로딩이 필요한 값은 여기서 미리 꺼낸다
        Board board = post.getBoard();
        eventPublisher.publishEvent(new ReportSubmittedEvent(
                report.getId(), TargetType.POST, postId.toString(), reason, normalizedDetail,
                reporter.getUserName(), reporterId.toString(), report.getCreatedAt(),
                author.getUserName(), board.getBoardId(), board.getMatch().getMission()));
        return report.getId();
    }

    @Override
    @Transactional
    public Long reportUser(UUID reporterId, UUID reportedUserId, ReportReason reason, String detail) {
        if (reason == ReportReason.FAKE_MISSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유저 신고에는 사용할 수 없는 신고 사유입니다.");
        }
        String normalizedDetail = validate(reason, detail);
        if (reportedUserId.equals(reporterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 신고할 수 없습니다.");
        }
        Users reporter = getReporter(reporterId);
        Users reportedUser = reportRepositoryPort.findUser(reportedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (reportRepositoryPort.existsUserReport(reporterId, reportedUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신고한 유저입니다.");
        }

        Report report = reportRepositoryPort.save(Report.ofUser(reporter, reportedUser, reason, normalizedDetail));

        eventPublisher.publishEvent(new ReportSubmittedEvent(
                report.getId(), TargetType.USER, reportedUserId.toString(), reason, normalizedDetail,
                reporter.getUserName(), reporterId.toString(), report.getCreatedAt(),
                null, null, null));
        return report.getId();
    }

    /**
     * 사유와 상세 내용을 검증하고, 공백뿐인 detail은 null로 정리해 돌려준다.
     */
    private String validate(ReportReason reason, String detail) {
        if (reason == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신고 사유는 필수입니다.");
        }
        String normalized = (detail == null || detail.isBlank()) ? null : detail.strip();
        if (normalized != null && normalized.length() > DETAIL_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "상세 내용은 " + DETAIL_MAX_LENGTH + "자 이하로 입력해주세요.");
        }
        if (reason == ReportReason.OTHER && normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기타 사유로 신고할 때는 상세 내용을 입력해주세요.");
        }
        return normalized;
    }

    /**
     * 토큰은 유효한데 유저가 없는 경우. PostPersistenceAdapter.getUser와 같은 기준으로 401을 낸다.
     */
    private Users getReporter(UUID reporterId) {
        return reportRepositoryPort.findUser(reporterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }
}
