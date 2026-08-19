package com.raon.tikitaka.domain.ranking;

import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 라운드별 지역 점수 — 게시물이 올라올 때마다 (stage_id, location_id) 행에 실시간 누적된다.
 * 순위 컬럼은 두지 않는다 — 라운드 순위는 조회 시 ORDER BY location_score DESC로 계산하고,
 * 시즌 확정 순위는 season_location_result.final_rank가 담당한다.
 */
@Entity
@Table(name = "location_ranking",
        uniqueConstraints = @UniqueConstraint(name = "uk_location_ranking_stage_location",
                columnNames = {"stage_id", "location_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationRanking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_ranking_id")
    private Long locationRankingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "location_score", nullable = false)
    private Long locationScore;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
