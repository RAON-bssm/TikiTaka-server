package com.raon.tikitaka.application.location;

import com.raon.tikitaka.application.location.in.GetLocationListUseCase;
import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.domain.location.Location;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService implements GetLocationListUseCase {

    private final LocationRepositoryPort locationRepositoryPort;

    @Override
    public List<Location> getLocations() {
        // findAll은 순서를 보장하지 않는다. 가입 화면 목록이 호출마다 뒤바뀌지 않도록 id로 고정한다
        return locationRepositoryPort.findAll().stream()
                .sorted(Comparator.comparing(Location::getLocationId))
                .toList();
    }
}
