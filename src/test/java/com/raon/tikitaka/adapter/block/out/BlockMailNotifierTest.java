package com.raon.tikitaka.adapter.block.out;

import com.raon.tikitaka.application.block.BlockSubmittedEvent;
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

class BlockMailNotifierTest {

    private static final String ADMIN = "raon.bssm@gmail.com";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void 차단_메일_제목과_본문() {
        BlockMailNotifier notifier = new BlockMailNotifier(mailSender, ADMIN, "bot@gmail.com", "app-password");

        notifier.onBlockSubmitted(event("차단자", "대상"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly(ADMIN);
        assertThat(message.getSubject()).isEqualTo("[티키타카 차단] 유저 차단 #7 (blocker-id → blocked-id)");
        assertThat(message.getText())
                .contains("차단 ID: 7", "차단한 유저: 차단자 (blocker-id)", "차단당한 유저: 대상 (blocked-id)",
                        "Asia/Seoul");
    }

    @Test
    void 제목에_닉네임이_들어가지_않는다() {
        BlockSubmittedEvent event = event("제목에\n들어가면 안 되는 닉네임", "Bcc: evil@example.com");

        String subject = BlockMailNotifier.buildSubject(event);

        assertThat(subject).isEqualTo("[티키타카 차단] 유저 차단 #7 (blocker-id → blocked-id)");
        assertThat(subject).doesNotContain("닉네임", "evil", "\n");
        assertThat(BlockMailNotifier.buildBody(event)).contains("제목에\n들어가면 안 되는 닉네임");
    }

    @Test
    void 메일_발송이_실패해도_예외를_던지지_않는다() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));
        BlockMailNotifier notifier = new BlockMailNotifier(mailSender, ADMIN, "bot@gmail.com", "app-password");

        assertThatCode(() -> notifier.onBlockSubmitted(event("차단자", "대상"))).doesNotThrowAnyException();
    }

    @Test
    void 메일_계정이_비어_있으면_발송을_건너뛴다() {
        BlockMailNotifier notifier = new BlockMailNotifier(mailSender, ADMIN, "", "");

        notifier.onBlockSubmitted(event("차단자", "대상"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private static BlockSubmittedEvent event(String blockerName, String blockedName) {
        return new BlockSubmittedEvent(7L, blockerName, "blocker-id", blockedName, "blocked-id", LocalDateTime.now());
    }
}
