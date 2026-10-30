package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.Territory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TerritoryJpaRepository extends JpaRepository<Territory, Long> {
}
