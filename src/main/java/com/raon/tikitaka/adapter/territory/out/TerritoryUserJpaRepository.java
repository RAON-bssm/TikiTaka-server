package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** 땅따먹기 배치용 유저 일괄 조회. 기존 UserJpaRepository·UserRepositoryPort는 건드리지 않는다 */
public interface TerritoryUserJpaRepository extends JpaRepository<Users, UUID> {
}
