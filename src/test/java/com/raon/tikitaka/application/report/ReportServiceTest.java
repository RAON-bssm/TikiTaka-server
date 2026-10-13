package com.raon.tikitaka.application.report;

import com.raon.tikitaka.application.report.out.ReportRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.ReportReason;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.report.Report;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepositoryPort reportRepositoryPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReportService reportService;

    private final UUID reporterId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepositoryPort, eventPublisher);
    }

    // ===== 게시물 신고 =====

    @Test
    void 게시물_신고_정상_접수() {
        Users reporter = user(reporterId, "신고자");
        Post post = post(user(authorId, "작성자"));
        when(reportRepositoryPort.findUser(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepositoryPort.findActivePost(postId)).thenReturn(Optional.of(post));
        when(reportRepositoryPort.existsPostReport(reporterId, postId)).thenReturn(false);
        when(reportRepositoryPort.save(any(Report.class))).thenAnswer(inv -> withId(inv.getArgument(0), 15L));

        Long reportId = reportService.reportPost(reporterId, postId, ReportReason.ABUSE, "  욕설이 포함되어 있어요 ");

        assertThat(reportId).isEqualTo(15L);
        ArgumentCaptor<ReportSubmittedEvent> captor = ArgumentCaptor.forClass(ReportSubmittedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ReportSubmittedEvent event = captor.getValue();
        assertThat(event.reportId()).isEqualTo(15L);
        assertThat(event.targetType()).isEqualTo(ReportSubmittedEvent.TargetType.POST);
        assertThat(event.detail()).isEqualTo("욕설이 포함되어 있어요");
        assertThat(event.postAuthorName()).isEqualTo("작성자");
        assertThat(event.boardId()).isEqualTo(3L);
        assertThat(event.boardMission()).isEqualTo("동네 맛집 인증");
    }

    @Test
    void 본인_게시물_신고는_400() {
        Users reporter = user(reporterId, "신고자");
        Post ownPost = post(reporter);
        when(reportRepositoryPort.findUser(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepositoryPort.findActivePost(postId)).thenReturn(Optional.of(ownPost));

        assertStatus(() -> reportService.reportPost(reporterId, postId, ReportReason.SPAM, null),
                HttpStatus.BAD_REQUEST);
        verify(reportRepositoryPort, never()).save(any());
    }

    @Test
    void OTHER인데_detail이_없으면_400() {
        assertStatus(() -> reportService.reportPost(reporterId, postId, ReportReason.OTHER, "   "),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> reportService.reportPost(reporterId, postId, ReportReason.OTHER, null),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void detail이_200자를_넘으면_400() {
        assertStatus(() -> reportService.reportPost(reporterId, postId, ReportReason.ABUSE, "가".repeat(201)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void reason이_없으면_400() {
        assertStatus(() -> reportService.reportPost(reporterId, postId, null, null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void 이미_신고한_게시물은_409() {
        Users reporter = user(reporterId, "신고자");
        Post post = post(user(authorId, "작성자"));
        when(reportRepositoryPort.findUser(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepositoryPort.findActivePost(postId)).thenReturn(Optional.of(post));
        when(reportRepositoryPort.existsPostReport(reporterId, postId)).thenReturn(true);

        assertStatus(() -> reportService.reportPost(reporterId, postId, ReportReason.SPAM, null),
                HttpStatus.CONFLICT);
        verify(reportRepositoryPort, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 없거나_삭제된_게시물은_404() {
        Users reporter = user(reporterId, "신고자");
        when(reportRepositoryPort.findUser(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepositoryPort.findActivePost(postId)).thenReturn(Optional.empty());

        assertStatus(() -> reportService.reportPost(reporterId, postId, ReportReason.SPAM, null),
                HttpStatus.NOT_FOUND);
    }

    // ===== 유저 신고 =====

    @Test
    void 유저_신고_정상_접수() {
        UUID targetId = UUID.randomUUID();
        Users reporter = user(reporterId, "신고자");
        when(reportRepositoryPort.findUser(reporterId)).thenReturn(Optional.of(reporter));
        Users target = user(targetId, "대상");
        when(reportRepositoryPort.findUser(targetId)).thenReturn(Optional.of(target));
        when(reportRepositoryPort.existsUserReport(reporterId, targetId)).thenReturn(false);
        when(reportRepositoryPort.save(any(Report.class))).thenAnswer(inv -> withId(inv.getArgument(0), 16L));

        Long reportId = reportService.reportUser(reporterId, targetId, ReportReason.OTHER, "사칭 계정이에요");

        assertThat(reportId).isEqualTo(16L);
        ArgumentCaptor<ReportSubmittedEvent> captor = ArgumentCaptor.forClass(ReportSubmittedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo(ReportSubmittedEvent.TargetType.USER);
        assertThat(captor.getValue().targetId()).isEqualTo(targetId.toString());
    }

    @Test
    void 유저_신고에서_FAKE_MISSION은_400() {
        assertStatus(() -> reportService.reportUser(reporterId, UUID.randomUUID(), ReportReason.FAKE_MISSION, null),
                HttpStatus.BAD_REQUEST);
        verify(reportRepositoryPort, never()).save(any());
    }

    @Test
    void 자기_자신_신고는_400() {
        assertStatus(() -> reportService.reportUser(reporterId, reporterId, ReportReason.ABUSE, null),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void 없는_유저_신고는_404() {
        UUID targetId = UUID.randomUUID();
        Users reporter = user(reporterId, "신고자");
        when(reportRepositoryPort.findUser(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepositoryPort.findUser(targetId)).thenReturn(Optional.empty());

        assertStatus(() -> reportService.reportUser(reporterId, targetId, ReportReason.ABUSE, null),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void 이미_신고한_유저는_409() {
        UUID targetId = UUID.randomUUID();
        Users reporter = user(reporterId, "신고자");
        when(reportRepositoryPort.findUser(reporterId)).thenReturn(Optional.of(reporter));
        Users target = user(targetId, "대상");
        when(reportRepositoryPort.findUser(targetId)).thenReturn(Optional.of(target));
        when(reportRepositoryPort.existsUserReport(reporterId, targetId)).thenReturn(true);

        assertStatus(() -> reportService.reportUser(reporterId, targetId, ReportReason.ABUSE, null),
                HttpStatus.CONFLICT);
    }

    // ===== helpers =====

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }

    private static Users user(UUID id, String name) {
        Users user = mock(Users.class);
        // 검증 실패 경로에서는 일부 getter가 불리지 않으므로 lenient로 둔다
        lenient().when(user.getUserId()).thenReturn(id);
        lenient().when(user.getUserName()).thenReturn(name);
        return user;
    }

    private static Post post(Users author) {
        Match match = mock(Match.class);
        lenient().when(match.getMission()).thenReturn("동네 맛집 인증");
        Board board = mock(Board.class);
        lenient().when(board.getBoardId()).thenReturn(3L);
        lenient().when(board.getMatch()).thenReturn(match);
        Post post = mock(Post.class);
        when(post.getUserId()).thenReturn(author);
        lenient().when(post.getBoard()).thenReturn(board);
        return post;
    }

    private static Report withId(Report report, Long id) {
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }
}
