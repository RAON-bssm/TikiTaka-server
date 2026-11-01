package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.Dong;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DongJpaRepository extends JpaRepository<Dong, Long> {

    List<Dong> findAllByLocation_LocationIdOrderByDongCode(Long locationId);

    long countByLocation_LocationId(Long locationId);
}
