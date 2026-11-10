package com.raon.tikitaka.application.ranking;

import com.raon.tikitaka.application.equipment.EquippedItems;
import com.raon.tikitaka.application.equipment.in.GetEquippedItemsUseCase;
import com.raon.tikitaka.application.location.out.LocationRepositoryPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.ranking.out.SeasonResultRepositoryPort;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.global.config.SeasonProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 개인 랭킹에 착용 정보를 붙이는 규칙. 상위 목록과 본인 순위의 유저를 합쳐 한 번만 조회하고,
 * 랭킹 쿼리 선택(차단 제외, 100명 상한)과 순서는 그대로여야 한다.
 */
class RankingServiceUserRankingTest {

    private static final long STAGE_ID = 7L;

    private final StageRepositoryPort stageRepositoryPort = mock(StageRepositoryPort.class);
    private final RankingRepositoryPort rankingRepositoryPort = mock(RankingRepositoryPort.class);
    private final GetEquippedItemsUseCase getEquippedItemsUseCase = mock(GetEquippedItemsUseCase.class);
    private final RankingService rankingService = new RankingService(stageRepositoryPort, rankingRepositoryPort,
            mock(SeasonResultRepositoryPort.class), mock(LocationRepositoryPort.class), new SeasonProperties(7, 4, 30, 1),
            mock(PostReviewQueuePort.class), getEquippedItemsUseCase);

    private final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Stage stage = mock(Stage.class);
        when(stage.getStageId()).thenReturn(STAGE_ID);
        when(stageRepositoryPort.findCurrent(any())).thenReturn(Optional.of(stage));
    }

    @Test
    void 상위_목록과_본인_순위의_유저를_합쳐_한_번에_조회한다() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UserRankRow firstRow = row(first, 1);
        UserRankRow secondRow = row(second, 2);
        UserRankRow myRow = row(me, 150);
        when(rankingRepositoryPort.findTopUserRankingExcludingBlockedBy(STAGE_ID, me, 100))
                .thenReturn(List.of(firstRow, secondRow));
        when(rankingRepositoryPort.findMyUserRanking(STAGE_ID, me)).thenReturn(Optional.of(myRow));

        UserRankingResult result = rankingService.getUserRanking(me);

        assertThat(captureRequestedIds()).containsExactlyInAnyOrder(first, second, me);
        // 순서와 본인 순위는 쿼리 결과 그대로다
        assertThat(result.ranking()).containsExactly(firstRow, secondRow);
        assertThat(result.myRanking()).isSameAs(myRow);
        verify(rankingRepositoryPort, never()).findTopUserRanking(anyLong(), anyInt());
    }

    @Test
    void 본인이_상위_목록에_있어도_한_번만_조회한다() {
        UUID other = UUID.randomUUID();
        UserRankRow myRow = row(me, 1);
        List<UserRankRow> top = List.of(myRow, row(other, 2));
        when(rankingRepositoryPort.findTopUserRankingExcludingBlockedBy(STAGE_ID, me, 100)).thenReturn(top);
        when(rankingRepositoryPort.findMyUserRanking(STAGE_ID, me)).thenReturn(Optional.of(myRow));

        rankingService.getUserRanking(me);

        assertThat(captureRequestedIds()).hasSize(2).containsExactlyInAnyOrder(me, other);
    }

    @Test
    void 본인이_미참여면_myRanking은_null이고_상위_목록만_조회한다() {
        UUID other = UUID.randomUUID();
        List<UserRankRow> top = List.of(row(other, 1));
        when(rankingRepositoryPort.findTopUserRankingExcludingBlockedBy(STAGE_ID, me, 100)).thenReturn(top);
        when(rankingRepositoryPort.findMyUserRanking(STAGE_ID, me)).thenReturn(Optional.empty());

        UserRankingResult result = rankingService.getUserRanking(me);

        assertThat(result.myRanking()).isNull();
        assertThat(result.ranking()).hasSize(1);
        assertThat(captureRequestedIds()).containsExactly(other);
    }

    @Test
    void 조회_결과가_유저별로_붙고_맵에_없는_유저는_빈_값이다() {
        UUID dressed = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        EquippedItems dressedItems = new EquippedItems(List.of(mock(Product.class)), Map.of());
        List<UserRankRow> top = List.of(row(dressed, 1), row(missing, 2));
        when(rankingRepositoryPort.findTopUserRankingExcludingBlockedBy(STAGE_ID, me, 100)).thenReturn(top);
        when(rankingRepositoryPort.findMyUserRanking(STAGE_ID, me)).thenReturn(Optional.empty());
        when(getEquippedItemsUseCase.getEquippedItemsByUserIds(any())).thenReturn(Map.of(dressed, dressedItems));

        UserRankingResult result = rankingService.getUserRanking(me);

        assertThat(result.equipmentOf(dressed)).isSameAs(dressedItems);
        assertThat(result.equipmentOf(missing).products()).isEmpty();
        assertThat(result.equipmentOf(missing).colors()).isEmpty();
    }

    // ===== helpers =====

    @SuppressWarnings("unchecked")
    private Collection<UUID> captureRequestedIds() {
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(getEquippedItemsUseCase, times(1)).getEquippedItemsByUserIds(captor.capture());
        return captor.getValue();
    }

    private static UserRankRow row(UUID userId, int rank) {
        UserRankRow row = mock(UserRankRow.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getUserName()).thenReturn("유저" + rank);
        when(row.getUserRank()).thenReturn(rank);
        when(row.getUserScore()).thenReturn(1000L - rank);
        return row;
    }
}
