package com.raon.tikitaka.application.user;

import com.raon.tikitaka.application.user.in.DeactivateInactiveUsersUseCase;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.RankingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService implements DeactivateInactiveUsersUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final RankingProperties rankingProperties;

    /**
     * 휴면 전환 배치 — lastActiveAt이 dormantDays(7일) 이상 지난 ACTIVE 유저를 DORMANT로.
     * ⚠️ 스케줄러(9단계)에 올리지 않는다 — 로그인 브랜치가 병합되어 로그인이 touch()를
     * 호출하기 전까지는, 이 배치가 돌면 로그인만 하는 유저까지 전부 휴면 처리된다.
     */
    @Override
    public void execute() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(rankingProperties.dormantDays());
        List<Users> users = userRepositoryPort.findAllActiveLastActiveBefore(threshold);
        for (Users user : users) {
            user.markDormant();
        }
        if (users.size() > 0) {
            log.info("휴면 전환 완료 — 유저 {}명 (기준: {} 이전 활동)", users.size(), threshold);
        }
    }
}
