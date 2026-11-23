package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.TerritoryWage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TerritoryWageJpaRepository extends JpaRepository<TerritoryWage, Long> {

    @Query("select w from TerritoryWage w where w.user.userId = :userId order by w.paidOn desc, w.id desc")
    List<TerritoryWage> findRecentByUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("select w from TerritoryWage w where w.user.userId = :userId and w.cycle.cycleId = :cycleId " +
            "order by w.paidOn desc, w.id desc")
    List<TerritoryWage> findRecentByUserAndCycle(@Param("userId") UUID userId, @Param("cycleId") Long cycleId,
                                                 Pageable pageable);

    @Query("select distinct w.user.userId from TerritoryWage w where w.paidOn = :paidOn")
    List<UUID> findUserIdsByPaidOn(@Param("paidOn") LocalDate paidOn);
}
