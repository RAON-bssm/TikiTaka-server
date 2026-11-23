package com.raon.tikitaka.domain.push;

import com.raon.tikitaka.domain.enums.AppPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FCM 등록 토큰(push_token 테이블). 기기(앱 설치)마다 하나라 token이 UNIQUE이고, 한 유저는 여러 기기를 가질 수 있다(1:N).
 *
 * 쓰기는 PushTokenJpaRepository의 네이티브 upsert(INSERT ... ON CONFLICT (token))로만 한다.
 * 같은 기기에서 다른 계정으로 로그인하면 user_id가 새 계정으로 바뀐다(이전 계정 알림이 그 기기로 가지 않게).
 * user_id는 users를 FK로 참조하지 않는다. 탈퇴해도 유저 행이 남고(WITHDRAWN), 토큰은 발송 실패나 정리 배치로 지워진다.
 */
@Entity
@Table(name = "push_token",
        indexes = @Index(name = "idx_push_token_user_id", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_token_id")
    private Long id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    /** FCM 등록 토큰. 보통 150~200자라 넉넉히 잡는다 */
    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private AppPlatform platform;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 마지막으로 앱이 이 토큰을 등록한 시각. 오래된 토큰 정리 기준 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
