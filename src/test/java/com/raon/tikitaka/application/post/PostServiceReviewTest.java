package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.block.out.BlockRepositoryPort;
import com.raon.tikitaka.application.post.out.PostLikeRepositoryPort;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.post.out.PostReviewQueuePort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.enums.UserStatus;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.post.PostStatus;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.RankingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 비동기 업로드(createPending)와 심사 완료 반영(completeReview). 잠근 행의 상태를 보고 한 번만 반영하는지 본다.
 */
class PostServiceReviewTest {

    private static final long STAGE_ID = 7L;
    private static final long HOME_ID = 1L;

    private final PostRepositoryPort postRepositoryPort = mock(PostRepositoryPort.class);
    private final RankingRepositoryPort rankingRepositoryPort = mock(RankingRepositoryPort.class);
    private final PostReviewQueuePort queuePort = mock(PostReviewQueuePort.class);
    private final RankingProperties rankingProperties = new RankingProperties(67.76, 5, 7, 20);
    private PostService postService;

    private final UUID postId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private Users author;
    private Location home;
    private Board board;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepositoryPort, mock(PostLikeRepositoryPort.class), rankingRepositoryPort,
                rankingProperties, mock(BlockRepositoryPort.class), queuePort);

        home = mock(Location.class);
        lenient().when(home.getLocationId()).thenReturn(HOME_ID);
        lenient().when(home.getLocationName()).thenReturn("해운대구");
        Location away = mock(Location.class);
        lenient().when(away.getLocationId()).thenReturn(2L);
        Stage stage = mock(Stage.class);
        lenient().when(stage.getStageId()).thenReturn(STAGE_ID);
        lenient().when(stage.getAvgLocationMemberCount()).thenReturn(5.0);
        Match match = mock(Match.class);
        lenient().when(match.getStage()).thenReturn(stage);
        lenient().when(match.getTeam1()).thenReturn(home);
        lenient().when(match.getTeam2()).thenReturn(away);
        lenient().when(match.getTeam1MemberCount()).thenReturn(5);
        board = mock(Board.class);
        lenient().when(board.getBoardId()).thenReturn(3L);
        lenient().when(board.getMatch()).thenReturn(match);
        lenient().when(postRepositoryPort.getBoard(3L)).thenReturn(board);

        author = mock(Users.class);
        lenient().when(author.getUserId()).thenReturn(authorId);
        lenient().when(author.getCurrentLocation()).thenReturn(home);
        lenient().when(postRepositoryPort.getUser(authorId)).thenReturn(author);
    }

    // ===== createPending =====

    @Test
    void 비동기_업로드는_PENDING으로_저장하고_포인트와_점수를_건드리지_않는다() {
        when(author.isAtHome()).thenReturn(true);
        when(postRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        postService.createPending(authorId, 3L, "내용", "photo.jpg");

        ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
        verify(postRepositoryPort).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PostStatus.PENDING);
        assertThat(saved.getValue().getScore()).isNull();
        assertThat(saved.getValue().getAiReview()).isNull();
        // 작성 시점 스냅샷은 지금 저장한다
        assertThat(saved.getValue().getTeamLocation()).isSameAs(home);
        assertThat(saved.getValue().isLocationScored()).isTrue();
        verify(author).touch();
        verify(author, never()).earnPoint(anyInt());
        verify(rankingRepositoryPort, never()).addUserScore(anyLong(), any(), anyLong());
        verify(rankingRepositoryPort, never()).addLocationScore(anyLong(), anyLong(), anyLong());
    }

    // ===== completeReview =====

    @Test
    void PENDING인_활성_글이면_점수를_기록하고_포인트를_먼저_적립한_뒤_점수를_더한다() {
        lockedPost(true, PostStatus.PENDING, true);

        boolean applied = postService.completeReview(postId, 80, "좋아요");

        assertThat(applied).isTrue();
        InOrder order = inOrder(queuePort, author, rankingRepositoryPort);
        order.verify(queuePort).complete(postId, 80, "좋아요");
        // accrueScores의 upsert가 영속성 컨텍스트를 비우므로 포인트 적립이 반드시 먼저다
        order.verify(author).earnPoint(271);      // round(80 * 67.76 / 20)
        order.verify(rankingRepositoryPort).addUserScore(STAGE_ID, authorId, 5421L);     // round(80 * 67.76)
        order.verify(rankingRepositoryPort).addLocationScore(STAGE_ID, HOME_ID, 5421L);
    }

    @Test
    void 원정_글은_개인_점수만_오른다() {
        lockedPost(true, PostStatus.PENDING, false);

        postService.completeReview(postId, 80, "좋아요");

        verify(rankingRepositoryPort).addUserScore(STAGE_ID, authorId, 5421L);
        verify(rankingRepositoryPort, never()).addLocationScore(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 삭제된_글은_반영하지_않는다() {
        lockedPost(false, PostStatus.PENDING, true);

        assertThat(postService.completeReview(postId, 80, "좋아요")).isFalse();
        assertNothingApplied();
    }

    @Test
    void 이미_PENDING이_아니면_반영하지_않는다_두_번째_호출과_EXPIRED() {
        for (PostStatus status : new PostStatus[]{PostStatus.DONE, PostStatus.EXPIRED, PostStatus.FAILED}) {
            lockedPost(true, status, true);
            assertThat(postService.completeReview(postId, 80, "좋아요")).isFalse();
        }
        assertNothingApplied();
    }

    @Test
    void 탈퇴한_작성자의_글도_똑같이_반영한다() {
        when(author.getStatus()).thenReturn(UserStatus.WITHDRAWN);
        lockedPost(true, PostStatus.PENDING, true);

        assertThat(postService.completeReview(postId, 80, "좋아요")).isTrue();

        verify(author).earnPoint(271);
        verify(rankingRepositoryPort).addUserScore(STAGE_ID, authorId, 5421L);
        verify(rankingRepositoryPort).addLocationScore(eq(STAGE_ID), eq(HOME_ID), anyLong());
    }

    @Test
    void 글이_없으면_반영하지_않는다() {
        when(postRepositoryPort.findByIdForUpdate(postId)).thenReturn(Optional.empty());

        assertThat(postService.completeReview(postId, 80, "좋아요")).isFalse();
        assertNothingApplied();
    }

    private void lockedPost(boolean active, PostStatus status, boolean locationScored) {
        Post post = mock(Post.class);
        lenient().when(post.getPostId()).thenReturn(postId);
        lenient().when(post.isActive()).thenReturn(active);
        lenient().when(post.getStatus()).thenReturn(status);
        lenient().when(post.isPending()).thenReturn(status == PostStatus.PENDING);
        lenient().when(post.getUserId()).thenReturn(author);
        lenient().when(post.getBoard()).thenReturn(board);
        lenient().when(post.getTeamLocation()).thenReturn(home);
        lenient().when(post.isLocationScored()).thenReturn(locationScored);
        when(postRepositoryPort.findByIdForUpdate(postId)).thenReturn(Optional.of(post));
    }

    private void assertNothingApplied() {
        verify(queuePort, never()).complete(any(), anyInt(), any());
        verify(author, never()).earnPoint(anyInt());
        verify(rankingRepositoryPort, never()).addUserScore(anyLong(), any(), anyLong());
        verify(rankingRepositoryPort, never()).addLocationScore(anyLong(), anyLong(), anyLong());
    }
}
