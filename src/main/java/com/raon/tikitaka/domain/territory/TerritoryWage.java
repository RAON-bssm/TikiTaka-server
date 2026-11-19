package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일급 지급 이력. 하루에 땅 주인 한 명당 한 행이다.
 * 지급 당시의 땅 개수·땅값 합계·비율을 남겨 금액을 다시 계산해 볼 수 있게 한다.
 * (사이클, 유저, 날짜) 유니크라 같은 날 두 번 지급되면 저장 단계에서 막힌다.
 */
@Entity
@Table(name = "territory_wage",
        uniqueConstraints = @UniqueConstraint(name = "uk_territory_wage_cycle_user_day",
                columnNames = {"cycle_id", "user_id", "paid_on"}),
        indexes = @Index(name = "idx_territory_wage_user_paid", columnList = "user_id, paid_on"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryWage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wage_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private TerritoryCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "land_count", nullable = false)
    private int landCount;

    @Column(name = "total_land_price", nullable = false)
    private long totalLandPrice;

    @Column(name = "rate", nullable = false)
    private double rate;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static TerritoryWage of(TerritoryCycle cycle, Users user, LocalDate paidOn, int landCount,
                                   long totalLandPrice, double rate, int amount, LocalDateTime now) {
        TerritoryWage w = new TerritoryWage();
        w.cycle = cycle;
        w.user = user;
        w.paidOn = paidOn;
        w.landCount = landCount;
        w.totalLandPrice = totalLandPrice;
        w.rate = rate;
        w.amount = amount;
        w.createdAt = now;
        return w;
    }
}
