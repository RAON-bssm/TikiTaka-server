package com.raon.tikitaka.application.user;

import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.user.in.ApplyLocationSwapUseCase;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 지역 스위칭 적용 — 라운드 시작 직후(9단계 스케줄 00:10) 1회 실행.
 * StageService에 넣지 않고 별도 클래스로 둔 이유: EnsureFutureStagesUseCase와
 * ApplyLocationSwapUseCase 둘 다 execute()라서 한 클래스가 두 인터페이스를 구현할 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LocationSwapService implements ApplyLocationSwapUseCase {

    private final StageRepositoryPort stageRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    @Override
    public void execute() {
        Optional<Stage> current = stageRepositoryPort.findCurrent(LocalDateTime.now());
        if (current.isEmpty()) {
            log.warn("진행 중인 라운드가 없어 지역 스위칭을 건너뜁니다.");
            return;
        }

        Stage stage = current.get();
        if (stage.isSwapApplied()) {
            return;     // 이 라운드엔 이미 적용됨 — 재실행돼도 두 번 바뀌지 않는다(멱등)
        }

        List<Users> users = userRepositoryPort.findAllWithPendingLocationSwap();
        for (Users user : users) {
            user.applyLocationSwap();   // main ↔ sub 교환 + 예약 해제 (더티 체킹으로 UPDATE)
        }
        stage.markSwapApplied();

        log.info("지역 스위칭 적용 완료 — 유저 {}명 (season {}, round {})",
                users.size(), stage.getSeason(), stage.getRound());
    }
}
