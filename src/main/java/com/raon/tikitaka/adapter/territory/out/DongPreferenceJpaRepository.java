package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.territory.DongPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

public interface DongPreferenceJpaRepository extends JpaRepository<DongPreference, Long> {

    @Modifying
    @Query("delete from DongPreference p where p.user.userId in :userIds")
    void deleteAllByUserIds(@Param("userIds") Collection<UUID> userIds);
}
