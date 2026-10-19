package com.raon.tikitaka.domain.block;

import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유저 차단. blocker가 blocked를 차단했다는 한 방향 관계이고, 필터도 blocker의 화면에만 적용된다.
 *
 * 유니크 제약은 동시 요청의 이중 차단을 막는 최후 방어선이다. 정상 흐름은 서비스가 먼저 존재 여부를 보고
 * 이미 차단 중이면 아무 것도 하지 않는다. 체크 제약은 자기 자신 차단을 직접 INSERT하는 경우까지 막는다.
 */
@Entity
@Table(name = "user_block",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_block_blocker_blocked",
                columnNames = {"blocker_id", "blocked_id"}),
        check = @CheckConstraint(name = "ck_user_block_not_self", constraint = "blocker_id <> blocked_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private Users blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private Users blocked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static UserBlock of(Users blocker, Users blocked) {
        UserBlock block = new UserBlock();
        block.blocker = blocker;
        block.blocked = blocked;
        block.createdAt = LocalDateTime.now();
        return block;
    }
}
