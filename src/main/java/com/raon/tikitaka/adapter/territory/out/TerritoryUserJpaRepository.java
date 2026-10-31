package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.domain.user.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * 땅따먹기용 유저 조회·포인트 갱신. 기존 UserJpaRepository·UserRepositoryPort는 건드리지 않는다.
 * 포인트는 엔티티 변경 감지 대신 원자적 UPDATE로 바꾼다. 통행료는 다른 유저(대표)의 포인트를 올리므로,
 * 읽고-고치고-쓰는 방식이면 그 대표가 동시에 상점을 쓸 때 한쪽 변경이 사라질 수 있다.
 */
public interface TerritoryUserJpaRepository extends JpaRepository<Users, UUID> {

    /** 잔액이 충분할 때만 차감한다. 바뀐 행 수가 0이면 포인트 부족 */
    @Modifying(flushAutomatically = true)
    @Query("update Users u set u.point = u.point - :amount where u.userId = :userId and u.point >= :amount")
    int deductPoint(@Param("userId") UUID userId, @Param("amount") int amount);

    @Modifying(flushAutomatically = true)
    @Query("update Users u set u.point = u.point + :amount where u.userId = :userId")
    int addPoint(@Param("userId") UUID userId, @Param("amount") int amount);

    @Query("select u.point from Users u where u.userId = :userId")
    Integer findPoint(@Param("userId") UUID userId);
}
