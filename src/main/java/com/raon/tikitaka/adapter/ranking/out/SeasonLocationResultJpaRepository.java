package com.raon.tikitaka.adapter.ranking.out;

import com.raon.tikitaka.domain.ranking.SeasonLocationResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeasonLocationResultJpaRepository extends JpaRepository<SeasonLocationResult, Long> {

    boolean existsBySeason(Integer season);
}
