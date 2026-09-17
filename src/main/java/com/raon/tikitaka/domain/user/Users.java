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
     * 본진. 지역 점수가 쌓이는 곳이고 회원가입 시 필수라 null일 수 없다.
     * 라운드 중에는 바뀌지 않고 예약을 통해 다음 라운드 시작 시에만 바뀐다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_location_id", nullable = false)
    private Location mainLocation;

    /**
     * 지금 있는 지역. 본진을 떠나 다른 동네에 가 있을 수 있으므로 메인과 다를 수 있고
     * 라운드 도중에도 즉시 바꿀 수 있다. 게시물은 이 지역의 게시판에만 쓸 수 있다.
     *
     * 레거시 행 보호를 위해 컬럼은 nullable이고, null이면 본진에 있는 것으로 본다.
     * 조회는 항상 getCurrentLocation()을 써야 하고 필드를 직접 읽으면 안 된다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_location_id")
    private Location currentLocation;

    /**
     * 다음 라운드 시작 직후 본진이 될 예약 지역. 예약이 없으면 null이다.
     * 라운드 도중 본진이 바뀌면 지역 점수 집계가 흔들리므로 본진 변경은 항상 예약으로만 받는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_location_id")
    private Location pendingLocation;

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
        user.currentLocation = mainLocation;    // 가입 직후에는 본진에 있다
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
     * 지금 있는 지역. 한 번도 옮긴 적이 없거나 컬럼 추가 이전 데이터면 본진을 돌려준다.
     */
    public Location getCurrentLocation() {
        return this.currentLocation != null ? this.currentLocation : this.mainLocation;
    }

    /**
     * 본진에 있는지 여부. 본진에서 쓴 게시물만 지역 점수까지 올라간다.
     */
    public boolean isAtHome() {
        return getCurrentLocation().getLocationId().equals(this.mainLocation.getLocationId());
    }

    /**
     * 현재 지역 이동. 라운드 도중에도 즉시 반영된다.
     */
    public void moveTo(Location location) {
        this.currentLocation = location;
    }

    /**
     * 본진 변경 예약. 실제 이동은 다음 라운드 시작 직후 배치가 수행한다.
     * 이미 예약이 있으면 새 지역으로 덮어쓴다.
     */
    public void reserveLocationChange(Location location) {
        this.pendingLocation = location;
    }

    /**
     * 예약 취소. 다음 라운드가 시작되기 전에만 의미가 있다.
     */
    public void cancelLocationChange() {
        this.pendingLocation = null;
    }

    /**
     * 예약된 본진 변경 적용. 라운드 시작 직후 배치에서만 호출해야 한다.
     * 본진을 옮기는 건 이사라서 현재 지역도 같이 새 본진으로 데려간다.
     * 적용과 동시에 예약을 비워 재실행돼도 두 번 이동하지 않는다.
     */
    public void applyPendingLocation() {
        if (this.pendingLocation == null) {
            return;
        }
        this.mainLocation = this.pendingLocation;
        this.currentLocation = this.pendingLocation;
        this.pendingLocation = null;
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
