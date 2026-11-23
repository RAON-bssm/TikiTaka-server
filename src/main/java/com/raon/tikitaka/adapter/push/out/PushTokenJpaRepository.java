package com.raon.tikitaka.adapter.push.out;

import com.raon.tikitaka.domain.push.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 쓰기 메서드에 @Transactional을 붙인 이유: 발송 경로(PushService.sendToUsers)는 FCM 호출 때문에 일부러
 * 트랜잭션 없이 돈다. 그 경로에서 무효 토큰을 지울 때도 이 메서드 단위로 커밋되게 한다.
 * 서비스 트랜잭션 안에서 부르면 그 트랜잭션에 합류한다.
 */
public interface PushTokenJpaRepository extends JpaRepository<PushToken, Long> {

    /**
     * 같은 토큰이 있으면 주인·플랫폼·갱신 시각을 바꾼다.
     * 같은 기기에서 A가 로그아웃하지 않고 B로 로그인해도 토큰이 B에게 넘어가 A의 알림이 B 기기로 가지 않는다.
     */
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO push_token (user_id, token, platform, created_at, updated_at)
            VALUES (:userId, :token, :platform, :now, :now)
            ON CONFLICT (token) DO UPDATE
               SET user_id    = EXCLUDED.user_id,
                   platform   = EXCLUDED.platform,
                   updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("userId") UUID userId, @Param("token") String token, @Param("platform") String platform,
                @Param("now") LocalDateTime now);

    @Query("select p.token from PushToken p where p.userId in :userIds")
    List<String> findTokensByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    @Transactional
    @Modifying
    @Query("delete from PushToken p where p.userId = :userId and p.token = :token")
    int deleteByUserIdAndToken(@Param("userId") UUID userId, @Param("token") String token);

    @Transactional
    @Modifying
    @Query("delete from PushToken p where p.token in :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    @Transactional
    @Modifying
    @Query("delete from PushToken p where p.updatedAt < :threshold")
    int deleteByUpdatedAtBefore(@Param("threshold") LocalDateTime threshold);
}
