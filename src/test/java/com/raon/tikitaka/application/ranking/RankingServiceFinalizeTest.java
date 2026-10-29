package com.raon.tikitaka.application.ranking;

import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.global.config.SeasonProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 시즌 확정이 순위 계산 전에 그 시즌의 PENDING 글을 EXPIRED로 바꾸는지 본다.
 */
class RankingServiceFinalizeTest {

    private final StageRepositoryPort stageRepositoryPort = mock(StageRepositoryPort.class);
    private final RankingRepositoryPort rankingRepositoryPort = mock(RankingRepositoryPort.class);
    private final SeasonResultRepositoryPort seasonResultRepositoryPort = mock(SeasonResultRepositoryPort.class);
    private final PostReviewQueuePort postReviewQueuePort = mock(PostReviewQueuePort.class);
    private final RankingService rankingService = new RankingService(stageRepositoryPort, rankingRepositoryPort,
            seasonResultRepositoryPort, mock(LocationRepositoryPort.class), new SeasonProperties(7, 4, 30, 1),
            postReviewQueuePort);

    @Test
    void 순위를_계산하기_전에_그_시즌의_PENDING_글을_EXPIRED로_바꾼다() {
        when(stageRepositoryPort.findAll()).thenReturn(season(3, LocalDateTime.now().minusDays(1)));
        when(rankingRepositoryPort.calculateSeasonRanking(3)).thenReturn(List.of());

        rankingService.execute();

        InOrder order = inOrder(postReviewQueuePort, rankingRepositoryPort);
        order.verify(postReviewQueuePort).lockSeasonExclusive(3);
        order.verify(postReviewQueuePort).expirePendingBySeason(3);
        order.verify(rankingRepositoryPort).calculateSeasonRanking(3);
    }

    @Test
    void 진행_중인_시즌은_확정하지_않으므로_PENDING도_그대로다() {
        when(stageRepositoryPort.findAll()).thenReturn(season(3, LocalDateTime.now().plusDays(1)));

        rankingService.execute();

        verify(postReviewQueuePort, never()).expirePendingBySeason(anyInt());
    }

    @Test
    void 이미_확정된_시즌은_다시_건드리지_않는다() {
        when(stageRepositoryPort.findAll()).thenReturn(season(3, LocalDateTime.now().minusDays(1)));
        when(seasonResultRepositoryPort.existsBySeason(3)).thenReturn(true);

        rankingService.execute();

        verify(postReviewQueuePort, never()).expirePendingBySeason(anyInt());
    }

    private static List<Stage> season(int season, LocalDateTime lastEndedAt) {
        List<Stage> stages = new ArrayList<>();
        for (int round = 1; round <= 4; round++) {
            LocalDateTime endedAt = lastEndedAt.minusDays((4 - round) * 7L);
            stages.add(Stage.create(season, round, endedAt.minusDays(7), endedAt));
        }
        return stages;
    }
}
