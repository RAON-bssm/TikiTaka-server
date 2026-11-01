package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.application.territory.out.RandomPort;
import com.raon.tikitaka.domain.enums.AssignmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 한 구 안의 대표 자동 배치.
 * 1) 대표 순위대로 희망 동 1→2→3순위 중 비어 있는 첫 동을 준다.
 * 2) 희망 동을 못 받은 대표(미등록, 셋 다 참)는 1)이 모두 끝난 뒤 남은 동을 섞어 순위대로 준다.
 * 랜덤 배정을 뒤로 미루는 이유: 앞 순위의 랜덤 배정이 뒤 순위의 희망 동을 빼앗지 않게 하기 위해서다.
 * 랜덤 대상은 원하는 동이 없거나 이미 못 받은 사람이라 미뤄도 잃는 것이 없다.
 */
@Component
@RequiredArgsConstructor
public class AssignmentPolicy {

    private static final AssignmentType[] BY_PRIORITY =
            {AssignmentType.PREFERENCE_1, AssignmentType.PREFERENCE_2, AssignmentType.PREFERENCE_3};

    private final RandomPort randomPort;

    /** @param preferredDongIds 1·2·3순위 순서. 없으면 빈 목록 */
    public record Candidate(UUID userId, List<Long> preferredDongIds) {
    }

    public record Assignment(UUID userId, Long dongId, AssignmentType type) {
    }

    /** @param rankedCandidates 대표 순위 순서. 인원은 동 수 이하여야 한다 */
    public List<Assignment> assign(List<Candidate> rankedCandidates, Collection<Long> dongIds) {
        if (rankedCandidates.size() > dongIds.size()) {
            throw new IllegalArgumentException("대표가 동 수보다 많습니다.");
        }
        Set<Long> available = new LinkedHashSet<>(dongIds);
        Map<UUID, Assignment> result = new LinkedHashMap<>();
        List<Candidate> unassigned = new ArrayList<>();

        for (Candidate c : rankedCandidates) {
            Assignment a = null;
            List<Long> prefs = c.preferredDongIds();
            for (int i = 0; i < Math.min(3, prefs.size()) && a == null; i++) {
                if (available.remove(prefs.get(i))) {
                    a = new Assignment(c.userId(), prefs.get(i), BY_PRIORITY[i]);
                }
            }
            if (a == null) {
                unassigned.add(c);
            }
            result.put(c.userId(), a);   // 순위 순서를 유지하려고 자리를 먼저 잡는다
        }

        List<Long> rest = new ArrayList<>(available);
        randomPort.shuffle(rest);
        Iterator<Long> it = rest.iterator();
        for (Candidate c : unassigned) {
            result.put(c.userId(), new Assignment(c.userId(), it.next(), AssignmentType.RANDOM));
        }
        return List.copyOf(result.values());
    }
}
