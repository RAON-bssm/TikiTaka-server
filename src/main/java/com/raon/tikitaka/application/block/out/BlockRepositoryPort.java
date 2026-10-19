package com.raon.tikitaka.application.block.out;

import com.raon.tikitaka.domain.block.UserBlock;
import com.raon.tikitaka.domain.user.Users;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockRepositoryPort {

    Optional<Users> findUser(UUID userId);

    boolean existsByBlockerAndBlocked(UUID blockerId, UUID blockedId);

    /**
     * 즉시 flush해서 유니크 제약 위반을 이 호출 안에서 드러낸다.
     * 동시 요청으로 같은 차단이 먼저 만들어졌으면 500 대신 {@link com.raon.tikitaka.application.block.AlreadyBlockedException}을 던진다.
     */
    UserBlock save(UserBlock block);

    /**
     * 차단한 적이 없으면 0건 삭제로 끝난다.
     */
    void deleteByBlockerAndBlocked(UUID blockerId, UUID blockedId);

    /**
     * blocker가 차단한 목록. 차단당한 유저를 같이 읽고 최신순이다. 탈퇴한 유저는 제외한다.
     */
    List<UserBlock> findAllByBlocker(UUID blockerId);

    /**
     * 탈퇴 시 그 유저가 차단자이거나 대상인 차단을 모두 지운다.
     */
    void deleteAllInvolving(UUID userId);
}
