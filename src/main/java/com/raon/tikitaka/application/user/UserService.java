package com.raon.tikitaka.application.user;

import com.raon.tikitaka.application.user.in.DeactivateInactiveUsersUseCase;
import com.raon.tikitaka.application.user.in.GetMyInfoUseCase;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.RankingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService implements DeactivateInactiveUsersUseCase, GetMyInfoUseCase {

    private final UserRepositoryPort userRepositoryPort;
    private final RankingProperties rankingProperties;

    /**
     * 휴면 전환 배치. lastActiveAt이 dormantDays 이상 지난 ACTIVE 유저를 DORMANT로 바꾼다.
     * 로그인과 게시물 작성이 touch()로 lastActiveAt을 갱신하는 것이 전제다.
     */
    @Override
    public void execute() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(rankingProperties.dormantDays());
        List<Users> users = userRepositoryPort.findAllActiveLastActiveBefore(threshold);
        for (Users user : users) {
            user.markDormant();
        }
        if (users.size() > 0) {
            log.info("휴면 전환 완료. 유저 {}명 (기준: {} 이전 활동)", users.size(), threshold);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Users getMyInfo(UUID userId) {
        Optional<Users> user = userRepositoryPort.findByIdWithLocations(userId);
        if (user.isEmpty()) {
            // 토큰은 유효한데 유저가 없는 경우는 탈퇴 등 비정상 상황뿐이다
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return user.get();
    }
}
