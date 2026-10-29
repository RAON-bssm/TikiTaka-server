package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.config.PostUploadLimitProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 업로드 한도 판단. 어떤 글을 셀지(상태, 삭제 여부, 기간)는 쿼리 조건이라 PostReviewQueryTest에서 실제 DB로 보고,
 * 여기서는 쿼리에 넘기는 조건과 센 결과로 통과·거절과 메시지를 정하는지 본다.
 */
class PostUploadLimitServiceTest {

    private final PostRepositoryPort postRepositoryPort = mock(PostRepositoryPort.class);
    private final UUID userId = UUID.randomUUID();

    private PostUploadLimitService service(boolean enabled) {
        return new PostUploadLimitService(postRepositoryPort,
                new PostUploadLimitProperties(enabled, 2, Duration.ofMinutes(10), 2));
    }

    @Test
    void 한도가_꺼져_있으면_검사도_쿼리도_하지_않는다() {
        service(false).checkUploadLimit(userId);

        verifyNoInteractions(postRepositoryPort);
    }

    @Test
    void 기간_안_글이_한도보다_적으면_통과한다() {
        recentUploads(LocalDateTime.now().minusMinutes(3));
        when(postRepositoryPort.countActivePendingByUserId(userId)).thenReturn(1L);

        assertThatCode(() -> service(true).checkUploadLimit(userId)).doesNotThrowAnyException();
    }

    @Test
    void 기간_안_글이_한도와_같으면_429와_남은_시간을_알려준다() {
        LocalDateTime now = LocalDateTime.now();
        // 최근 순: 1분 전, 3분 30초 전. 두 번째(가장 오래된) 글이 10분을 넘기는 시각은 6분 30초 뒤이고 올림하면 7분
        recentUploads(now.minusMinutes(1), now.minusMinutes(3).minusSeconds(30));

        assertTooMany(() -> service(true).checkUploadLimit(userId),
                "10분에 2번까지 올릴 수 있어요. 7분 후에 다시 올려주세요.");
        verify(postRepositoryPort, never()).countActivePendingByUserId(any());
    }

    @Test
    void 기간_시작은_지금에서_window를_뺀_시각이고_세는_상태는_PENDING_DONE_REJECTED다() {
        recentUploads();

        service(true).checkUploadLimit(userId);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postRepositoryPort).findRecentUploadTimes(eq(userId), since.capture(),
                eq(PostUploadLimitService.COUNTED_STATUSES), eq(2));
        assertThat(since.getValue()).isCloseTo(LocalDateTime.now().minusMinutes(10), within(5, ChronoUnit.SECONDS));
        // FAILED, EXPIRED는 사용자 잘못이 아니라 세지 않는다
        assertThat(PostUploadLimitService.COUNTED_STATUSES)
                .containsExactlyInAnyOrder(PostStatus.PENDING, PostStatus.DONE, PostStatus.REJECTED)
                .doesNotContain(PostStatus.FAILED, PostStatus.EXPIRED);
    }

    @Test
    void 다시_올릴_수_있는_시각은_한도번째로_최근인_글과_window의_합이다() {
        LocalDateTime now = LocalDateTime.now();
        // 9분 전 글이 1분 뒤에 기간 밖으로 나간다
        recentUploads(now.minusSeconds(10), now.minusMinutes(9));

        assertTooMany(() -> service(true).checkUploadLimit(userId),
                "10분에 2번까지 올릴 수 있어요. 1분 후에 다시 올려주세요.");
    }

    @Test
    void 남은_시간은_분_단위로_올림하고_최소_1분이다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);

        assertThat(PostUploadLimitService.minutesUntil(now, now.plusMinutes(4))).isEqualTo(4);
        assertThat(PostUploadLimitService.minutesUntil(now, now.plusMinutes(4).plusSeconds(1))).isEqualTo(5);
        assertThat(PostUploadLimitService.minutesUntil(now, now.plusSeconds(5))).isEqualTo(1);
        assertThat(PostUploadLimitService.minutesUntil(now, now)).isEqualTo(1);
        assertThat(PostUploadLimitService.minutesUntil(now, now.minusSeconds(30))).isEqualTo(1);
    }

    @Test
    void 심사_대기_글이_한도와_같으면_429() {
        recentUploads(LocalDateTime.now().minusMinutes(20));
        when(postRepositoryPort.countActivePendingByUserId(userId)).thenReturn(2L);

        assertTooMany(() -> service(true).checkUploadLimit(userId),
                "심사 중인 게시물이 2개 있어요. 심사가 끝난 뒤에 올려주세요.");
    }

    @Test
    void 두_한도를_모두_넘으면_횟수_한도_메시지가_나온다() {
        LocalDateTime now = LocalDateTime.now();
        recentUploads(now.minusMinutes(1), now.minusMinutes(2));
        when(postRepositoryPort.countActivePendingByUserId(userId)).thenReturn(2L);

        assertTooMany(() -> service(true).checkUploadLimit(userId),
                "10분에 2번까지 올릴 수 있어요. 8분 후에 다시 올려주세요.");
    }

    @Test
    void 메시지의_숫자는_설정값을_따른다() {
        PostUploadLimitService custom = new PostUploadLimitService(postRepositoryPort,
                new PostUploadLimitProperties(true, 3, Duration.ofMinutes(30), 5));
        LocalDateTime now = LocalDateTime.now();
        when(postRepositoryPort.findRecentUploadTimes(eq(userId), any(), any(), anyInt()))
                .thenReturn(List.of(now.minusMinutes(1), now.minusMinutes(2), now.minusMinutes(5)));

        assertTooMany(() -> custom.checkUploadLimit(userId),
                "30분에 3번까지 올릴 수 있어요. 25분 후에 다시 올려주세요.");

        when(postRepositoryPort.findRecentUploadTimes(eq(userId), any(), any(), anyInt())).thenReturn(List.of());
        when(postRepositoryPort.countActivePendingByUserId(userId)).thenReturn(5L);
        assertTooMany(() -> custom.checkUploadLimit(userId),
                "심사 중인 게시물이 5개 있어요. 심사가 끝난 뒤에 올려주세요.");
    }

    private void recentUploads(LocalDateTime... createdAt) {
        when(postRepositoryPort.findRecentUploadTimes(eq(userId), any(), any(), anyInt()))
                .thenReturn(List.of(createdAt));
    }

    private static void assertTooMany(Runnable call, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }
}
