package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryCycle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TerritoryCycleRepositoryPort {

    /** 포인트 라운드나 땅따먹기 라운드로 stageId를 가진 사이클 */
    Optional<TerritoryCycle> findByStageId(Long stageId);

    boolean existsByPointStageId(Long pointStageId);

    TerritoryCycle save(TerritoryCycle cycle);

    /** 지금 진행 중인 사이클(포인트 라운드 시작 ~ 땅따먹기 라운드 종료). 라운드 두 개를 함께 조회한다 */
    Optional<TerritoryCycle> findCurrent(LocalDateTime now);

    /** 랭킹이 동결됐고 아직 대표를 뽑지 않았으며 땅따먹기 라운드가 끝나지 않은 사이클 */
    List<TerritoryCycle> findAllAwaitingSelection(LocalDateTime now);

    /** 땅따먹기 라운드가 시작됐고 아직 기본 미션 처리를 안 한 사이클 */
    List<TerritoryCycle> findAllAwaitingDefaults(LocalDateTime now);

    /** 땅따먹기 라운드가 끝났고 아직 랭킹을 확정하지 않은 사이클 */
    List<TerritoryCycle> findAllAwaitingFinalize(LocalDateTime now);

    /** 라운드 두 개를 함께 조회 */
    Optional<TerritoryCycle> findById(Long cycleId);

    /** 가장 최근에 랭킹이 확정된 사이클 */
    Optional<TerritoryCycle> findLatestFinalized();
}
