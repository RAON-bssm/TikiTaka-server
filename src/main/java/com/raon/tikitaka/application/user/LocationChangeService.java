package com.raon.tikitaka.application.user;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.user.in.ApplyLocationChangeUseCase;
import com.raon.tikitaka.application.user.in.ManageLocationChangeUseCase;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.user.Users;
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

/**
 * 지역 변경 예약 서비스. 유저의 예약 관리와 라운드 시작 직후의 일괄 적용을 담당한다.
 * EnsureFutureStagesUseCase와 ApplyLocationChangeUseCase 둘 다 execute()라서
 * 한 클래스가 두 인터페이스를 구현할 수 없어 StageService와 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LocationChangeService implements ApplyLocationChangeUseCase, ManageLocationChangeUseCase {

    private final StageRepositoryPort stageRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;
    private final LocationRepositoryPort locationRepositoryPort;

    @Override
    public void execute() {
        Optional<Stage> current = stageRepositoryPort.findCurrent(LocalDateTime.now());
        if (current.isEmpty()) {
            log.warn("진행 중인 라운드가 없어 지역 변경 적용을 건너뜁니다.");
            return;
        }

        Stage stage = current.get();
        if (stage.isLocationChangeApplied()) {
            return;     // 이 라운드에 이미 적용됐으면 재실행돼도 두 번 이동하지 않는다
        }

        List<Users> users = userRepositoryPort.findAllWithPendingLocationChange();
        for (Users user : users) {
            user.applyPendingLocation();    // 소속을 옮기고 예약을 비운다
        }
        stage.markLocationChangeApplied();

        log.info("지역 변경 적용 완료. 유저 {}명 (season {}, round {})",
                users.size(), stage.getSeason(), stage.getRound());
    }

    @Override
    public void reserve(UUID userId, Long locationId) {
        Users user = getUser(userId);

        if (locationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동네를 선택해주세요.");
        }
        Optional<Location> location = locationRepositoryPort.findById(locationId);
        if (location.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 동네입니다.");
        }
        if (locationId.equals(user.getMainLocation().getLocationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 소속된 동네입니다.");
        }

        user.reserveLocationChange(location.get());
    }

    @Override
    public void cancel(UUID userId) {
        getUser(userId).cancelLocationChange();
    }

    private Users getUser(UUID userId) {
        Optional<Users> user = userRepositoryPort.findByIdWithLocations(userId);
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return user.get();
    }
}
