package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.post.in.CheckPostUploadLimitUseCase;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.global.config.PostUploadLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 사용자별 게시물 업로드 한도. 두 가지를 본다.
 * - 업로드 횟수: 최근 window 안에 만든 글이 maxCount건 이상이면 거절
 * - 심사 대기: 삭제하지 않은 PENDING 글이 maxPending건 이상이면 거절
 * 둘 다 넘으면 다시 올릴 수 있는 시각을 알려주는 횟수 한도 메시지를 낸다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostUploadLimitService implements CheckPostUploadLimitUseCase {

    /**
     * 업로드 횟수로 세는 상태. FAILED(AI 장애)와 EXPIRED(시즌 확정)는 사용자 잘못이 아니라 세지 않는다.
     * 삭제한 글(is_active = false)은 센다. 안 세면 올리고 지우기를 반복해 한도를 피할 수 있다.
     */
    public static final Set<PostStatus> COUNTED_STATUSES = Set.of(PostStatus.PENDING, PostStatus.DONE, PostStatus.REJECTED);

    private final PostRepositoryPort postRepositoryPort;
    private final PostUploadLimitProperties properties;

    @Override
    public void checkUploadLimit(UUID userId) {
        if (!properties.enabled()) {
            return;
        }
        // 글 저장 시각(Post.create)과 같은 방식으로 재야 created_at과 맞는다
        LocalDateTime now = LocalDateTime.now();

        // 최근 글부터 maxCount건만 읽는다. maxCount건이 다 차면 한도에 닿은 것이고,
        // 그중 가장 오래된 글(maxCount번째로 최근인 글)이 기간 밖으로 나가는 시각부터 다시 올릴 수 있다
        List<LocalDateTime> recent = postRepositoryPort.findRecentUploadTimes(userId, now.minus(properties.window()),
                COUNTED_STATUSES, properties.maxCount());
        if (recent.size() >= properties.maxCount()) {
            LocalDateTime retryAt = recent.get(properties.maxCount() - 1).plus(properties.window());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, String.format(
                    "%d분에 %d번까지 올릴 수 있어요. %d분 후에 다시 올려주세요.",
                    properties.window().toMinutes(), properties.maxCount(), minutesUntil(now, retryAt)));
        }

        if (postRepositoryPort.countActivePendingByUserId(userId) >= properties.maxPending()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, String.format(
                    "심사 중인 게시물이 %d개 있어요. 심사가 끝난 뒤에 올려주세요.", properties.maxPending()));
        }
    }

    /** 남은 시간을 분 단위로 올림한다. 0분이나 음수로 안내하지 않도록 최소 1분이다 */
    static long minutesUntil(LocalDateTime now, LocalDateTime retryAt) {
        long millis = Duration.between(now, retryAt).toMillis();
        long minutes = (millis + 59_999) / 60_000;
        return Math.max(1, minutes);
    }
}
