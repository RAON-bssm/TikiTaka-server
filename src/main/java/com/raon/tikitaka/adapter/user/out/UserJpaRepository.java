package com.raon.tikitaka.adapter.user.out;

import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<Users, UUID> {

    @Query("""
            select u from Users u
            left join fetch u.mainLocation
            left join fetch u.subLocation
            where u.userId = :userId
            """)
    Optional<Users> findByIdWithLocations(UUID userId);
}
