package com.raon.tikitaka.application.territory;

import com.raon.tikitaka.application.territory.out.DongPreferenceRepositoryPort;
import com.raon.tikitaka.application.territory.out.DongRepositoryPort;
import com.raon.tikitaka.application.user.out.UserRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.territory.Dong;
import com.raon.tikitaka.domain.territory.DongPreference;
import com.raon.tikitaka.domain.user.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DongPreferenceServiceTest {

    private final DongPreferenceRepositoryPort preferencePort = mock(DongPreferenceRepositoryPort.class);
    private final DongRepositoryPort dongPort = mock(DongRepositoryPort.class);
    private final UserRepositoryPort userPort = mock(UserRepositoryPort.class);
    private final DongPreferenceService service = new DongPreferenceService(preferencePort, dongPort, userPort);
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Location home = mock(Location.class);
        when(home.getLocationId()).thenReturn(10L);
        Users user = mock(Users.class);
        when(user.getMainLocation()).thenReturn(home);
        when(userPort.findByIdWithLocations(userId)).thenReturn(Optional.of(user));
        // dong()이 안에서 when()을 쓰므로 바깥 when() 안에서 부르면 안 된다. 먼저 만들어 둔다
        List<Dong> homeDongs = List.of(dong(1L), dong(2L), dong(3L));
        when(dongPort.findAllByLocationId(10L)).thenReturn(homeDongs);
    }

    private static Dong dong(long code) {
        Dong d = mock(Dong.class);
        when(d.getDongCode()).thenReturn(code);
        return d;
    }

    @Test
    void 순서대로_순위를_매기고_기존_희망_동을_지운_뒤_저장한다() {
        List<DongPreference> saved = service.replacePreferences(userId, List.of(3L, 1L));

        assertThat(saved).extracting(DongPreference::getPriority).containsExactly(1, 2);
        assertThat(saved).extracting(p -> p.getDong().getDongCode()).containsExactly(3L, 1L);
        var order = inOrder(preferencePort);
        order.verify(preferencePort).deleteAllByUserIds(List.of(userId));
        order.verify(preferencePort).saveAll(saved);
    }

    @Test
    void 본진_구가_아닌_동_중복_4개_이상은_거절한다() {
        assertThatThrownBy(() -> service.replacePreferences(userId, List.of(99L)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.replacePreferences(userId, List.of(1L, 1L)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.replacePreferences(userId, List.of(1L, 2L, 3L, 4L)))
                .isInstanceOf(ResponseStatusException.class);
        verify(preferencePort, never()).saveAll(any());
    }
}
