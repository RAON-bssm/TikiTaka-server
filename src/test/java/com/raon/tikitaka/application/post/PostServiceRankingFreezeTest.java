package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.match.out.StageRepositoryPort;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.application.territory.TerritoryCycleService;
import com.raon.tikitaka.application.territory.out.TerritoryCycleRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.territory.TerritoryCycle;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.RankingProperties;
import com.raon.tikitaka.global.config.TerritoryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 선발전 랭킹 동결(포인트 라운드 4일차 종료)과 비동기 심사가 겹칠 때의 규칙.
 * 가산은 글 작성 시각으로, 삭제 차감은 현재 시각으로 동결 여부를 판단한다. 포인트는 동결과 무관하게 쌓인다.
 * 실제 TerritoryCycle과 TerritoryCycleService를 써서 동결 판단까지 함께 본다.
 */
class PostServiceRankingFreezeTest {

    private static final long POINT_STAGE_ID = 7L;
    private static final long HOME_ID = 1L;
    private static final TerritoryProperties TERRITORY = new TerritoryProperties(4, 5,
            new TerritoryProperties.LandPrice(2500), new TerritoryProperties.Defense(60));

    private final PostRepositoryPort postRepositoryPort = mock(PostRepositoryPort.class);
    private final RankingRepositoryPort rankingRepositoryPort = mock(RankingRepositoryPort.class);
    private final TerritoryCycleRepositoryPort cycleRepositoryPort = mock(TerritoryCycleRepositoryPort.class);
    private final PostService postService = new PostService(postRepositoryPort, mock(PostLikeRepositoryPort.class),
            rankingRepositoryPort, new RankingProperties(67.76, 5, 7, 20), mock(BlockRepositoryPort.class),
            mock(PostReviewQueuePort.class),
            new TerritoryCycleService(mock(StageRepositoryPort.class), cycleRepositoryPort, TERRITORY));

    private final UUID postId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final Users author = mock(Users.class);
    private final Location home = mock(Location.class);
    private Board board;

    /** 동결 시각이 freezeAt인 사이클의 포인트 라운드에 게시판을 둔다 */
    private void cycleFrozenAt(LocalDateTime freezeAt) {
        LocalDateTime pointStart = freezeAt.minusDays(4);
        Stage point = Stage.create(1, 1, pointStart, pointStart.plusDays(7));
        ReflectionTestUtils.setField(point, "stageId", POINT_STAGE_ID);
        ReflectionTestUtils.setField(point, "avgLocationMemberCount", 5.0);
        Stage battle = Stage.create(1, 2, pointStart.plusDays(7), pointStart.plusDays(14));
        ReflectionTestUtils.setField(battle, "stageId", 8L);
        TerritoryCycle cycle = TerritoryCycle.create(point, battle, 4);
        when(cycleRepositoryPort.findByStageId(POINT_STAGE_ID)).thenReturn(Optional.of(cycle));

        lenient().when(home.getLocationId()).thenReturn(HOME_ID);
        lenient().when(home.getLocationName()).thenReturn("해운대구");
        Match match = mock(Match.class);
        lenient().when(match.getStage()).thenReturn(point);
        lenient().when(match.getTeam1()).thenReturn(home);
        lenient().when(match.getTeam2()).thenReturn(home);
        lenient().when(match.getTeam1MemberCount()).thenReturn(5);
        board = mock(Board.class);
        lenient().when(board.getBoardId()).thenReturn(3L);
        lenient().when(board.getMatch()).thenReturn(match);
        lenient().when(postRepositoryPort.getBoard(3L)).thenReturn(board);
        lenient().when(author.getUserId()).thenReturn(authorId);
        lenient().when(author.getCurrentLocation()).thenReturn(home);
        lenient().when(postRepositoryPort.getUser(authorId)).thenReturn(author);
    }

    private void post(LocalDateTime createdAt, PostStatus status, Integer score) {
        Post post = mock(Post.class);
        lenient().when(post.isActive()).thenReturn(true);
        lenient().when(post.isPending()).thenReturn(status == PostStatus.PENDING);
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getBoard()).thenReturn(board);
        lenient().when(post.getTeamLocation()).thenReturn(home);
        lenient().when(post.isLocationScored()).thenReturn(true);
        lenient().when(post.getCreatedAt()).thenReturn(createdAt);
        lenient().when(post.getScore()).thenReturn(score);
        when(postRepositoryPort.findByIdForUpdate(postId)).thenReturn(Optional.of(post));
    }

    @Test
    void 동결_전에_쓴_글은_심사가_동결_뒤에_끝나도_점수가_반영된다() {
        LocalDateTime freezeAt = LocalDateTime.now().minusHours(1);
        cycleFrozenAt(freezeAt);
        post(freezeAt.minusMinutes(1), PostStatus.PENDING, null);

        assertThat(postService.completeReview(postId, 80, "좋아요")).isTrue();

        verify(author).earnPoint(271);
        verify(rankingRepositoryPort).addUserScore(POINT_STAGE_ID, authorId, 5421L);
        verify(rankingRepositoryPort).addLocationScore(POINT_STAGE_ID, HOME_ID, 5421L);
    }

    @Test
    void 동결_뒤에_쓴_글은_점수가_반영되지_않고_포인트만_쌓인다() {
        LocalDateTime freezeAt = LocalDateTime.now().minusHours(1);
        cycleFrozenAt(freezeAt);
        post(freezeAt, PostStatus.PENDING, null);

        assertThat(postService.completeReview(postId, 80, "좋아요")).isTrue();

        verify(author).earnPoint(271);
        verify(rankingRepositoryPort, never()).addUserScore(anyLong(), any(), anyLong());
        verify(rankingRepositoryPort, never()).addLocationScore(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 동결_뒤의_동기_업로드도_점수는_건너뛰고_포인트는_쌓인다() {
        cycleFrozenAt(LocalDateTime.now().minusHours(1));
        when(author.isAtHome()).thenReturn(true);
        when(postRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        postService.createPost(authorId, 3L, "내용", "photo.jpg", 80, "좋아요");

        verify(author).earnPoint(271);
        verify(rankingRepositoryPort, never()).addUserScore(anyLong(), any(), anyLong());
        verify(rankingRepositoryPort, never()).addLocationScore(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 동결_뒤에_삭제하면_동결_전_글이어도_점수를_되돌리지_않는다() {
        LocalDateTime freezeAt = LocalDateTime.now().minusHours(1);
        cycleFrozenAt(freezeAt);
        post(freezeAt.minusDays(1), PostStatus.DONE, 80);

        postService.deletePost(postId, authorId, false);

        verify(rankingRepositoryPort, never()).addUserScore(anyLong(), any(), anyLong());
        verify(rankingRepositoryPort, never()).addLocationScore(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 동결_전에_삭제하면_점수를_되돌린다() {
        cycleFrozenAt(LocalDateTime.now().plusDays(1));
        post(LocalDateTime.now().minusHours(2), PostStatus.DONE, 80);

        postService.deletePost(postId, authorId, false);

        verify(rankingRepositoryPort).addUserScore(POINT_STAGE_ID, authorId, -5421L);
        verify(rankingRepositoryPort).addLocationScore(POINT_STAGE_ID, HOME_ID, -5421L);
    }
}
