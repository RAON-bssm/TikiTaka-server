package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.AllowedWord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedWordJpaRepository extends JpaRepository<AllowedWord, Long> {
}
