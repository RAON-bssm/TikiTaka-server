package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.enums.RoundType;
import com.raon.tikitaka.domain.match.Stage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 땅따먹기 2주 사이클. 기존 Stage(7일 라운드) 두 개를 묶는다: 포인트 라운드 → 땅따먹기 라운드.
 * Stage 테이블은 건드리지 않고, 라운드 종류와 랭킹 동결 시각은 여기서 관리한다.
 */
@Entity
@Table(name = "territory_cycle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TerritoryCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cycle_id")
    private Long cycleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_stage_id", nullable = false, unique = true)
    private Stage pointStage;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_stage_id", nullable = false, unique = true)
    private Stage battleStage;

    /** 이 시각 이후의 글은 개인·동네 랭킹에 반영하지 않는다. 다음 사이클 시작까지 유지된다 */
    @Column(name = "ranking_freeze_at", nullable = false)
    private LocalDateTime rankingFreezeAt;

    @Column(name = "representatives_selected", nullable = false)
    @ColumnDefault("false")
    private boolean representativesSelected;

    /** 땅따먹기 주간 시작 처리(미션 없는 땅에 기본 미션) 완료 여부 */
    @Column(name = "defaults_applied", nullable = false)
    @ColumnDefault("false")
    private boolean defaultsApplied;

    @Column(name = "result_finalized", nullable = false)
    @ColumnDefault("false")
    private boolean resultFinalized;

    public static TerritoryCycle create(Stage pointStage, Stage battleStage, int rankingDays) {
        if (!pointStage.getEndedAt().equals(battleStage.getStartedAt())) {
            throw new IllegalArgumentException("포인트 라운드와 땅따먹기 라운드가 이어지지 않습니다.");
        }
        LocalDateTime freezeAt = pointStage.getStartedAt().plusDays(rankingDays);
        if (freezeAt.isAfter(pointStage.getEndedAt())) {
            throw new IllegalArgumentException("랭킹 동결 시각이 포인트 라운드를 넘어갑니다.");
        }
        TerritoryCycle cycle = new TerritoryCycle();
        cycle.pointStage = pointStage;
        cycle.battleStage = battleStage;
        cycle.rankingFreezeAt = freezeAt;
        return cycle;
    }

    public void markDefaultsApplied() {
        this.defaultsApplied = true;
    }

    public void markRepresentativesSelected() {
        this.representativesSelected = true;
    }

    /** 시각 기준 라운드 종류. 포인트 라운드 시작 전이나 땅따먹기 라운드 종료 후면 NONE */
    public RoundType roundTypeAt(LocalDateTime at) {
        if (at.isBefore(pointStage.getStartedAt()) || !at.isBefore(battleStage.getEndedAt())) {
            return RoundType.NONE;
        }
        return at.isBefore(pointStage.getEndedAt()) ? RoundType.POINT : RoundType.BATTLE;
    }

    /** 땅 세팅 기간(5~7일차): 대표 선발이 끝났고, 랭킹 동결 이후, 땅따먹기 라운드 시작 전 */
    public boolean isSetupPeriod(LocalDateTime at) {
        return representativesSelected && !at.isBefore(rankingFreezeAt) && at.isBefore(battleStage.getStartedAt());
    }

    /** stageId가 이 사이클에서 어떤 라운드인지. LAZY 프록시도 id 조회는 초기화 없이 된다 */
    public RoundType roundTypeOf(Long stageId) {
        if (Objects.equals(pointStage.getStageId(), stageId)) {
            return RoundType.POINT;
        }
        if (Objects.equals(battleStage.getStageId(), stageId)) {
            return RoundType.BATTLE;
        }
        return RoundType.NONE;
    }

    /**
     * 그 라운드에 쓴 글을 랭킹에 반영하지 않아야 하는지.
     * 땅따먹기 라운드는 통째로 동결, 포인트 라운드는 동결 시각부터 동결.
     */
    public boolean isRankingFrozen(Long stageId, LocalDateTime at) {
        return switch (roundTypeOf(stageId)) {
            case BATTLE -> true;
            case POINT -> !at.isBefore(rankingFreezeAt);
            case NONE -> false;
        };
    }
}
