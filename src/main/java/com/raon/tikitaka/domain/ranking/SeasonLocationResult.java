package com.raon.tikitaka.domain.ranking;

import com.raon.tikitaka.domain.location.Location;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시즌 최종 순위 — 시즌 종료 시 라운드 점수를 합산해 등수를 박제한다. 한 번 쓰이면 바뀌지 않는다.
 * 점수가 없는 동네도 final_score = 0으로 기록해 "미참여"와 "꼴등"을 구분한다.
 */
@Entity
@Table(name = "season_location_result",
        uniqueConstraints = @UniqueConstraint(name = "uk_season_location_result",
                columnNames = {"season", "location_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonLocationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "season", nullable = false)
    private Integer season;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "final_rank", nullable = false)
    private Integer finalRank;

    @Column(name = "final_score", nullable = false)
    private Long finalScore;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    public static SeasonLocationResult create(Integer season, Location location, Integer finalRank, Long finalScore) {
        SeasonLocationResult result = new SeasonLocationResult();
        result.season = season;
        result.location = location;
        result.finalRank = finalRank;
        result.finalScore = finalScore;
        result.confirmedAt = LocalDateTime.now();
        return result;
    }

}
