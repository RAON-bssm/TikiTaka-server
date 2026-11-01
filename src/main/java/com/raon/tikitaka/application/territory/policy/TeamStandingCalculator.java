package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.domain.territory.Territory;

import java.util.*;

/**
 * 땅따먹기 랭킹 계산. 팀 점수 = 그 팀(구)이 가진 땅의 땅값 합계(결정 #1).
 * 땅이 0개인 팀도 0점으로 포함한다. 같은 합계면 같은 순위(1, 2, 2, 4 방식)이고,
 * 표시 순서는 땅 수가 많은 팀, 그다음 구 id 순서다.
 */
public final class TeamStandingCalculator {

    private TeamStandingCalculator() {
    }

    public record Standing(int rank, Long locationId, long totalLandPrice, int ownedDongCount, double avgLandPrice) {
    }

    /** @param teamIds 순위에 올릴 팀 전체(땅따먹기 보드가 있는 구) */
    public static List<Standing> calculate(Collection<Territory> territories, Collection<Long> teamIds) {
        Map<Long, long[]> sums = new HashMap<>();   // locationId → {합계, 땅 수}
        teamIds.forEach(id -> sums.put(id, new long[2]));
        for (Territory t : territories) {
            if (!t.isOwned()) {
                continue;   // 주인 없는 땅은 어느 팀 점수에도 들어가지 않는다
            }
            long[] s = sums.computeIfAbsent(t.getOwnerLocation().getLocationId(), id -> new long[2]);
            s[0] += t.landPrice();
            s[1]++;
        }

        List<Map.Entry<Long, long[]>> sorted = new ArrayList<>(sums.entrySet());
        sorted.sort(Comparator.<Map.Entry<Long, long[]>>comparingLong(e -> -e.getValue()[0])
                .thenComparingLong(e -> -e.getValue()[1])
                .thenComparing(Map.Entry::getKey));

        List<Standing> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            long total = sorted.get(i).getValue()[0];
            int count = (int) sorted.get(i).getValue()[1];
            int rank = i > 0 && result.get(i - 1).totalLandPrice() == total ? result.get(i - 1).rank() : i + 1;
            result.add(new Standing(rank, sorted.get(i).getKey(), total, count, count == 0 ? 0 : (double) total / count));
        }
        return result;
    }
}
