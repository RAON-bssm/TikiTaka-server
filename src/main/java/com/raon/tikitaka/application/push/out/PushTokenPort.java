package com.raon.tikitaka.application.push.out;

import com.raon.tikitaka.domain.enums.AppPlatform;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PushTokenPort {

    /** 없으면 넣고, 있으면 user_id·platform·updated_at을 바꾼다 */
    void upsert(UUID userId, String token, AppPlatform platform, LocalDateTime now);

    /** 그 유저의 그 토큰만 지운다. 남의 토큰은 지우지 않는다 */
    void delete(UUID userId, String token);

    List<String> findTokensByUserIds(Collection<UUID> userIds);

    void deleteAllByTokens(Collection<String> tokens);

    /** updated_at이 threshold보다 이전인 토큰을 지우고 지운 수를 돌려준다 */
    int deleteNotUpdatedSince(LocalDateTime threshold);
}
