package com.raon.tikitaka.domain.match;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "stage",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage_season_round", columnNames = {"season", "round"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Long stageId;

    @Column(name = "season", nullable = false)
    private Integer season;

    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    /**
     * 매칭 시점의 평균 동네 인원. 전체 ACTIVE 유저 수를 동네 수로 나눈 값이다.
     * 매칭 배치가 채우기 전까지는 null이다.
     */
    @Column(name = "avg_location_member_count")
    private Double avgLocationMemberCount;

    public static Stage create(Integer season, Integer round, LocalDateTime startedAt, LocalDateTime endedAt) {
        Stage stage = new Stage();
        stage.season = season;
        stage.round = round;
        stage.startedAt = startedAt;
        stage.endedAt = endedAt;
        return stage;
    }

    public void assignAvgLocationMemberCount(double avgLocationMemberCount) {
        this.avgLocationMemberCount = avgLocationMemberCount;
    }

}
