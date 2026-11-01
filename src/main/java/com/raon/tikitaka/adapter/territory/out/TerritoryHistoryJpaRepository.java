package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryHistoryJpaRepository extends JpaRepository<TerritoryHistory, Long> {
}
