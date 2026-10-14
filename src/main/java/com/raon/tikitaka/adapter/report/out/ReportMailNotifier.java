package com.raon.tikitaka.adapter.report.out;

import com.raon.tikitaka.application.report.ReportSubmittedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 신고 접수 시 관리자에게 알림 메일을 보낸다.
 *
 * 커밋 이후(AFTER_COMMIT)에만 돌기 때문에 롤백된 신고로 메일이 나가는 일이 없고,
 * @Async로 요청 스레드와 분리돼 SMTP 지연·실패가 신고 응답에 번지지 않는다.
 * 발송 실패는 error 로그만 남기고 삼킨다. 신고는 이미 커밋됐으므로 접수 자체는 성공이다.
 *
 * MAIL_USERNAME/MAIL_PASSWORD가 비어 있어도 서버는 정상 기동하고, 그 경우 발송만 건너뛴다.
 */
@Slf4j
@Component
public class ReportMailNotifier {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (VV)");
    private static final int DETAIL_PREVIEW_LENGTH = 10000;

    private final JavaMailSender mailSender;
    private final String adminEmail;
    private final String username;
    private final String password;

    public ReportMailNotifier(JavaMailSender mailSender,
                              @Value("${report.admin-email:raon.bssm@gmail.com}") String adminEmail,
                              @Value("${spring.mail.username:}") String username,
                              @Value("${spring.mail.password:}") String password) {
        this.mailSender = mailSender;
        this.adminEmail = adminEmail;
        this.username = username;
        this.password = password;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReportSubmitted(ReportSubmittedEvent event) {
        if (username.isBlank() || password.isBlank()) {
            log.warn("메일 설정 없음, 발송 생략. reportId={}", event.reportId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username);
            message.setTo(adminEmail);
            message.setSubject(buildSubject(event));
            message.setText(buildBody(event));
            mailSender.send(message);
            log.info("신고 알림 메일 발송 완료. reportId={}", event.reportId());
        } catch (Exception e) {
            log.error("신고 알림 메일 발송 실패. reportId={}", event.reportId(), e);
        }
    }

    /**
     * 사용자 입력(detail 등)은 제목에 넣지 않는다. 헤더 주입과 제목 오염을 피하기 위함이다.
     */
    static String buildSubject(ReportSubmittedEvent event) {
        return String.format("[티키타카 신고] %s 신고 #%d (%s)",
                event.targetType().label(), event.reportId(), event.reason().name());
    }

    static String buildBody(ReportSubmittedEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("신고 ID: ").append(event.reportId()).append('\n');
        body.append("대상 종류: ").append(event.targetType().label()).append('\n');
        body.append("대상 ID: ").append(event.targetId()).append('\n');
        body.append("사유: ").append(event.reason().name())
                .append(" (").append(event.reason().getDescription()).append(")\n");
        body.append("상세 내용: ").append(preview(event.detail())).append('\n');
        body.append("신고자: ").append(event.reporterName()).append(" (").append(event.reporterId()).append(")\n");
        body.append("접수 시각: ").append(formatTime(event)).append('\n');
        if (event.targetType() == ReportSubmittedEvent.TargetType.POST) {
            body.append("게시물 작성자: ").append(event.postAuthorName()).append('\n');
            body.append("게시판: #").append(event.boardId())
                    .append(" (미션: ").append(event.boardMission()).append(")\n");
        }
        return body.toString();
    }

    private static String preview(String detail) {
        if (detail == null) {
            return "(없음)";
        }
        if (detail.length() <= DETAIL_PREVIEW_LENGTH) {
            return detail;
        }
        return detail.substring(0, DETAIL_PREVIEW_LENGTH) + "...";
    }

    /**
     * createdAt은 서버 기본 시간대 기준 LocalDateTime이다. 컨테이너 TZ와 무관하게 서울 시각으로 맞춘다.
     */
    private static String formatTime(ReportSubmittedEvent event) {
        if (event.createdAt() == null) {
            return "(알 수 없음)";
        }
        return event.createdAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(SEOUL).format(TIME_FORMAT);
    }
}
