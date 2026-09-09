package com.raon.tikitaka.domain.user;

import com.raon.tikitaka.domain.enums.LoginProvider;
import com.raon.tikitaka.domain.enums.UserRole;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.global.exception.InsufficientPointException;
import com.raon.tikitaka.global.exception.SubLocationNotSetException;
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
     * 메인 지역. 항상 현재 라운드의 소속과 일치하고 회원가입 시 필수라 null일 수 없다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_location_id", nullable = false)
    private Location mainLocation;

    /**
     * 두 번째 지역. 스위칭 대상일 뿐 점수 계산과는 무관하고 없을 수 있다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_location_id")
    private Location subLocation;

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

    /**
     * 지역 스위칭 예약 여부. true면 다음 라운드 시작 직후 배치가 메인과 서브를 교환한다.
     */
    @Column(name = "pending_location_swap", nullable = false)
    @ColumnDefault("false")
    private boolean pendingLocationSwap;

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
     * 서브 동네 설정과 변경. 스위칭 예약의 전제 조건이다.
     * 이미 예약이 걸린 상태에서 서브를 바꾸면 예약은 새 서브 동네 기준으로 적용된다.
     */
    public void assignSubLocation(Location location) {
        this.subLocation = location;
    }

    /**
     * 지역 스위칭 예약 취소. 다음 라운드 시작 전에만 의미가 있다.
     */
    public void cancelLocationSwap() {
        this.pendingLocationSwap = false;
    }

    /**
     * 지역 스위칭 예약. 실제 교환은 다음 라운드 시작 직후 배치가 수행한다.
     */
    public void requestLocationSwap() {
        if (this.subLocation == null) {
            throw new SubLocationNotSetException();
        }
        this.pendingLocationSwap = true;
    }

    /**
     * 예약된 스위칭 적용. 라운드 시작 직후 배치에서만 호출해야 한다.
     */
    public void applyLocationSwap() {
        if (!this.pendingLocationSwap) {
            return;
        }
        Location tmp = this.mainLocation;
        this.mainLocation = this.subLocation;
        this.subLocation = tmp;
        this.pendingLocationSwap = false;
    }

    /**
     * 메인과 서브 동네를 즉시 교환한다. 라운드 종료를 기다리지 않는다.
     * 걸려있던 스위칭 예약은 이미 반영된 셈이라 함께 해제한다 — 안 그러면
     * 다음 라운드 배치가 다시 한번 교환해 원래대로 되돌려버린다.
     */
    public void swapLocationImmediately() {
        if (this.subLocation == null) {
            throw new SubLocationNotSetException();
        }
        Location tmp = this.mainLocation;
        this.mainLocation = this.subLocation;
        this.subLocation = tmp;
        this.pendingLocationSwap = false;
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
