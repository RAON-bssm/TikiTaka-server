package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.in.ManageDongPreferenceUseCase;
import com.raon.tikitaka.application.territory.out.DongPreferenceRepositoryPort;
import com.raon.tikitaka.application.territory.out.DongRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.territory.Dong;
import com.raon.tikitaka.domain.territory.DongPreference;
import com.raon.tikitaka.domain.user.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DongPreferenceService implements ManageDongPreferenceUseCase {

    private static final int MAX_PREFERENCES = 3;

    private final DongPreferenceRepositoryPort preferenceRepositoryPort;
    private final DongRepositoryPort dongRepositoryPort;
    private final UserRepositoryPort userRepositoryPort;

    @Override
    @Transactional(readOnly = true)
    public List<DongPreference> getPreferences(UUID userId) {
        return preferenceRepositoryPort.findAllByUserId(userId);
    }

    /**
     * 본진 구의 동만 받는다(대표 배치가 구 단위라서). 순서가 곧 순위다.
     * 언제든 바꿀 수 있고, 대표 선발 배치가 도는 순간의 값이 배치에 쓰인다.
     */
    @Override
    public List<DongPreference> replacePreferences(UUID userId, List<Long> dongIds) {
        if (dongIds.size() > MAX_PREFERENCES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "희망 동은 최대 3개입니다.");
        }
        if (new HashSet<>(dongIds).size() != dongIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 동을 두 번 고를 수 없습니다.");
        }
        Users user = userRepositoryPort.findByIdWithLocations(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "유저를 찾을 수 없습니다."));

        // 본진 구의 동 목록으로 검증한다. 다른 구의 동이나 없는 동이면 거절
        Map<Long, Dong> homeDongs = dongRepositoryPort.findAllByLocationId(user.getMainLocation().getLocationId())
                .stream().collect(Collectors.toMap(Dong::getDongId, Function.identity()));
        List<DongPreference> preferences = new ArrayList<>();
        for (int i = 0; i < dongIds.size(); i++) {
            Dong dong = homeDongs.get(dongIds.get(i));
            if (dong == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본진 구의 동만 고를 수 있습니다.");
            }
            preferences.add(DongPreference.of(user, i + 1, dong));
        }

        preferenceRepositoryPort.deleteAllByUserIds(List.of(userId));
        preferenceRepositoryPort.saveAll(preferences);
        return preferences;
    }
}
