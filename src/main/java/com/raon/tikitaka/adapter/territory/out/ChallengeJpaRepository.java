package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.Challenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeJpaRepository extends JpaRepository<Challenge, UUID> {

    @Query("select c from Challenge c join fetch c.territory t join fetch t.dong where c.challengeId = :id")
    Optional<Challenge> findWithTerritoryById(@Param("id") UUID challengeId);
}
