package com.raon.tikitaka.adapter.report.out;

import com.raon.tikitaka.application.report.ReportSubmittedEvent;
import com.raon.tikitaka.application.report.ReportSubmittedEvent.TargetType;
import com.raon.tikitaka.domain.enums.ReportReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReportMailNotifierTest {

    private static final String ADMIN = "raon.bssm@gmail.com";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void 게시물_신고_메일_제목과_본문() {
        ReportMailNotifier notifier = new ReportMailNotifier(mailSender, ADMIN, "bot@gmail.com", "app-password");

        notifier.onReportSubmitted(postEvent());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly(ADMIN);
        assertThat(message.getSubject()).isEqualTo("[티키타카 신고] 게시물 신고 #15 (ABUSE)");
        assertThat(message.getText())
                .contains("신고 ID: 15", "대상 종류: 게시물", "사유: ABUSE", "욕설이 포함되어 있어요",
                        "신고자: 신고자", "게시물 작성자: 작성자", "게시판: #3", "Asia/Seoul");
    }

    @Test
    void 유저_신고_메일_제목에_사용자_입력이_들어가지_않는다() {
        ReportSubmittedEvent event = new ReportSubmittedEvent(16L, TargetType.USER, "target-id", ReportReason.ABUSE,
                "제목에\n들어가면 안 되는 내용", "신고자", "reporter-id", LocalDateTime.now(), null, null, null);

        assertThat(ReportMailNotifier.buildSubject(event)).isEqualTo("[티키타카 신고] 유저 신고 #16 (ABUSE)");
        assertThat(ReportMailNotifier.buildBody(event)).doesNotContain("게시물 작성자");
    }

    @Test
    void 메일_발송이_실패해도_예외를_던지지_않는다() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        ReportMailNotifier notifier = new ReportMailNotifier(mailSender, ADMIN, "bot@gmail.com", "app-password");

        assertThatCode(() -> notifier.onReportSubmitted(postEvent())).doesNotThrowAnyException();
    }

    @Test
    void 메일_계정이_비어_있으면_발송을_건너뛴다() {
        ReportMailNotifier notifier = new ReportMailNotifier(mailSender, ADMIN, "", "");

        notifier.onReportSubmitted(postEvent());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private static ReportSubmittedEvent postEvent() {
        return new ReportSubmittedEvent(15L, TargetType.POST, "post-id", ReportReason.ABUSE, "욕설이 포함되어 있어요",
                "신고자", "reporter-id", LocalDateTime.now(), "작성자", 3L, "동네 맛집 인증");
    }
}
