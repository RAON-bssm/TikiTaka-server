package com.raon.tikitaka.application.location;

import com.raon.tikitaka.application.location.in.CreateLocationUseCase;
import com.raon.tikitaka.application.location.in.GetLocationListUseCase;
import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.global.exception.DuplicateLocationNameException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService implements GetLocationListUseCase, CreateLocationUseCase {

    private final LocationRepositoryPort locationRepositoryPort;

    @Override
    public List<Location> getLocations() {
        // findAll은 순서를 보장하지 않는다. 가입 화면 목록이 호출마다 뒤바뀌지 않도록 id로 고정한다
        return locationRepositoryPort.findAll().stream()
                .sorted(Comparator.comparing(Location::getLocationId))
                .toList();
    }

    /**
     * 클래스에 걸린 readOnly = true는 쓰기를 막으므로 메서드 단위로 덮어쓴다.
     * 앞뒤 공백은 잘라낸다. " 강남 "과 "강남"이 다른 지역으로 들어가지 않도록.
     */
    @Override
    @Transactional
    public Location createLocation(String locationName) {
        String name = locationName.trim();
        if (locationRepositoryPort.existsByLocationName(name)) {
            throw new DuplicateLocationNameException(name);
        }
        return locationRepositoryPort.save(Location.of(name));
    }
}
