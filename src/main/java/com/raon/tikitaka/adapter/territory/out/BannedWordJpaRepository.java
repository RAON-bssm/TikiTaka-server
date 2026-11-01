package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.BannedWord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannedWordJpaRepository extends JpaRepository<BannedWord, Long> {
}
