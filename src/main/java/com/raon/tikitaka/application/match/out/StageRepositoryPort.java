package com.raon.tikitaka.application.match.out;

import com.raon.tikitaka.domain.match.Stage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StageRepositoryPort {

    Optional<Stage> findLatestByEndedAt();

    Stage save(Stage stage);

    Optional<Stage> findById(Long stageId);

    /**
     * 곧 시작하거나 진행 중인데 아직 매치가 하나도 없는 라운드 목록.
     * threshold가 now + matchOpenAheadDays 라서 라운드 시작 하루 전부터 매치를 미리 만든다.
     */
    List<Stage> findUpcomingWithoutMatch(LocalDateTime now, LocalDateTime threshold);

    /**
     * 지금 진행 중인 라운드. 라운드는 겹치지 않고 이어지므로 결과는 항상 0개 아니면 1개다.
     */
    Optional<Stage> findCurrent(LocalDateTime now);

    /**
     * 전체 라운드 목록. 시즌 확정이 시즌별 라운드 수와 종료 여부를 판정할 때 쓴다.
     */
    List<Stage> findAll();

    /**
     * 가장 최근에 종료된 라운드. 경기 결과 조회에 쓴다.
     */
    Optional<Stage> findLatestEnded(LocalDateTime now);
}
