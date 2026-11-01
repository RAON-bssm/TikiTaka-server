package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 대표 선발에서 유저를 한 번에 읽는다. 기존 UserJpaRepository·UserRepositoryPort는 건드리지 않는다 */
public interface TerritoryUserJpaRepository extends JpaRepository<Users, UUID> {
}
