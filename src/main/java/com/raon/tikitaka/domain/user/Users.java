package com.raon.tikitaka.domain.user;

import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.UserRole;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.global.exception.InsufficientPointException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "user_name", nullable = false, unique = true)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private LoginProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    /**
     * 소속 지역. 항상 현재 라운드의 소속과 일치하고 회원가입 시 필수라 null일 수 없다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_location_id", nullable = false)
    private Location mainLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    /**
     * ACTIVE 또는 DORMANT. 팀 인원수와 평균 동네 인원 집계는 ACTIVE만 센다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    /**
     * 마지막 활동 일시. 로그인과 게시물 작성 시 touch()로 갱신하고 dormantDays가 지나면 휴면 전환된다.
     */
    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    @Column(name = "point", nullable = false)
    @ColumnDefault("0")
    private Integer point;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 회원가입 팩토리 메서드. mainLocation은 필수다.
     * status와 lastActiveAt, 시각 필드는 prePersist가 채운다.
     */
    public static Users of(String userName, LoginProvider provider, String providerId, Location mainLocation) {
        Users user = new Users();
        user.userName = userName;
        user.provider = provider;
        user.providerId = providerId;
        user.mainLocation = mainLocation;
        user.role = UserRole.USER;
        user.point = 0;
        return user;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastActiveAt = now;
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
        if (this.point == null) {
            this.point = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 닉네임 변경. 중복 검증은 서비스 계층 책임이다.
     */
    public void changeUserName(String userName) {
        this.userName = userName;
    }

    public void usePoint(int amount) {
        if (this.point < amount) {
            throw new InsufficientPointException();
        }
        this.point -= amount;
    }

    /**
     * 소속 지역 변경. 다음 라운드 매칭부터 새 지역 기준으로 반영된다.
     */
    public void changeMainLocation(Location location) {
        this.mainLocation = location;
    }

    /**
     * 활동 기록. 로그인 성공과 게시물 작성 시 호출되고 휴면 유저는 ACTIVE로 복귀한다.
     */
    public void touch() {
        this.lastActiveAt = LocalDateTime.now();
        this.status = UserStatus.ACTIVE;
    }

    public void markDormant() {
        this.status = UserStatus.DORMANT;
    }

}
