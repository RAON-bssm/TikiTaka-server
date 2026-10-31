package com.raon.tikitaka.application.territory.out;

import com.raon.tikitaka.domain.territory.Representative;
import com.raon.tikitaka.domain.territory.Territory;
import com.raon.tikitaka.domain.territory.TerritoryHistory;
import com.raon.tikitaka.domain.user.Users;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 대표 선발 배치가 한 번에 저장하는 것들 */
public interface TerritorySetupPort {

    List<Users> findUsersByIds(Collection<UUID> userIds);

    List<Territory> saveTerritories(List<Territory> territories);

    void saveRepresentatives(List<Representative> representatives);

    void saveHistories(List<TerritoryHistory> histories);
}
