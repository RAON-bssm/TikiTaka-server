package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.location.Location;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사이클 종료 시점의 땅따먹기 랭킹 스냅샷. 합계(결정 #1 채택)와 평균을 함께 저장한다 */
@Entity
@Table(name = "team_land_result", uniqueConstraints =
        @UniqueConstraint(name = "uk_team_land_cycle_location", columnNames = {"cycle_id", "location_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamLandResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "team_land_result_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cycle_id", nullable = false)
    private TerritoryCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "total_land_price", nullable = false)
    private long totalLandPrice;

    @Column(name = "avg_land_price", nullable = false)
    private double avgLandPrice;

    @Column(name = "owned_dong_count", nullable = false)
    private int ownedDongCount;

    @Column(name = "rank", nullable = false)
    private int rank;

    public static TeamLandResult of(TerritoryCycle cycle, Location location, long total, int ownedCount, int rank) {
        TeamLandResult r = new TeamLandResult();
        r.cycle = cycle;
        r.location = location;
        r.totalLandPrice = total;
        r.ownedDongCount = ownedCount;
        r.avgLandPrice = ownedCount == 0 ? 0 : (double) total / ownedCount;
        r.rank = rank;
        return r;
    }
}
