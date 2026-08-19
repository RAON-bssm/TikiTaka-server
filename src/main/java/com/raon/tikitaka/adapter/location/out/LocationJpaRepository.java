package com.raon.tikitaka.adapter.location.out;

import com.raon.tikitaka.domain.location.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationJpaRepository extends JpaRepository<Location, Long> {
}
