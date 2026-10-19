package com.raon.tikitaka.adapter.block.out;

import com.raon.tikitaka.application.block.BlockSubmittedEvent;
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
 * 유저 차단 시 관리자에게 알림 메일을 보낸다. App Store 심사의 "차단하면 개발자에게 알림" 요구사항 대응이다.
 *
 * 동작 방식은 ReportMailNotifier와 같다. 커밋 이후(AFTER_COMMIT)에만 돌아 롤백된 차단으로 메일이 나가지 않고,
 * @Async로 요청 스레드와 분리돼 SMTP 지연·실패가 차단 응답에 번지지 않는다.
 * 발송 실패는 error 로그만 남기고 삼킨다. 차단은 이미 커밋됐으므로 차단 자체는 성공이다.
 *
 * MAIL_USERNAME/MAIL_PASSWORD가 비어 있어도 서버는 정상 기동하고, 그 경우 발송만 건너뛴다.
 * 수신자는 신고 알림과 같은 report.admin-email을 쓴다.
 */
@Slf4j
@Component
public class BlockMailNotifier {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (VV)");

    private final JavaMailSender mailSender;
    private final String adminEmail;
    private final String username;
    private final String password;

    public BlockMailNotifier(JavaMailSender mailSender,
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
    public void onBlockSubmitted(BlockSubmittedEvent event) {
        if (username.isBlank() || password.isBlank()) {
            log.warn("메일 설정 없음, 발송 생략. blockId={}", event.blockId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(username);
            message.setTo(adminEmail);
            message.setSubject(buildSubject(event));
            message.setText(buildBody(event));
            mailSender.send(message);
            log.info("차단 알림 메일 발송 완료. blockId={}", event.blockId());
        } catch (Exception e) {
            log.error("차단 알림 메일 발송 실패. blockId={}", event.blockId(), e);
        }
    }

    /**
     * 사용자 입력(닉네임)은 제목에 넣지 않는다. 헤더 주입과 제목 오염을 피하기 위함이다.
     * 제목에는 서버가 만든 UUID만 쓰고, 닉네임은 본문에만 넣는다.
     */
    static String buildSubject(BlockSubmittedEvent event) {
        return String.format("[티키타카 차단] 유저 차단 #%d (%s → %s)",
                event.blockId(), event.blockerId(), event.blockedId());
    }

    static String buildBody(BlockSubmittedEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("차단 ID: ").append(event.blockId()).append('\n');
        body.append("차단한 유저: ").append(event.blockerName()).append(" (").append(event.blockerId()).append(")\n");
        body.append("차단당한 유저: ").append(event.blockedName()).append(" (").append(event.blockedId()).append(")\n");
        body.append("차단 시각: ").append(formatTime(event)).append('\n');
        return body.toString();
    }

    /**
     * createdAt은 서버 기본 시간대 기준 LocalDateTime이다. 컨테이너 TZ와 무관하게 서울 시각으로 맞춘다.
     */
    private static String formatTime(BlockSubmittedEvent event) {
        if (event.createdAt() == null) {
            return "(알 수 없음)";
        }
        return event.createdAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(SEOUL).format(TIME_FORMAT);
    }
}
