package com.raon.tikitaka.application.post;

import com.raon.tikitaka.application.post.in.CreatePostUseCase;
import com.raon.tikitaka.application.post.in.DeletePostUseCase;
import com.raon.tikitaka.application.post.in.GetPostDetailUseCase;
import com.raon.tikitaka.application.post.in.GetPostListUseCase;
import com.raon.tikitaka.application.post.in.UpdatePostUseCase;
import com.raon.tikitaka.application.post.out.PostRepositoryPort;
import com.raon.tikitaka.application.ranking.out.RankingRepositoryPort;
import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.match.Stage;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;
import com.raon.tikitaka.global.config.RankingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService implements GetPostListUseCase, GetPostDetailUseCase, CreatePostUseCase, UpdatePostUseCase, DeletePostUseCase {

    private final PostRepositoryPort postRepositoryPort;
    private final RankingRepositoryPort rankingRepositoryPort;
    private final RankingProperties rankingProperties;

    @Override
    public List<Post> getPosts(Long boardId) {
        return postRepositoryPort.findAllActiveByBoardId(boardId);
    }

    @Override
    public Post getPost(UUID postId) {
        Optional<Post> post = postRepositoryPort.findActiveById(postId);
        if (post.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시물을 찾을 수 없습니다.");
        }
        return post.get();
    }

    @Override
    @Transactional
    public void createPost(UUID authorId, Long boardId, String content, String postImage, Integer score, String aiReview) {
        Users author = postRepositoryPort.getUser(authorId);
        Board board = postRepositoryPort.getBoard(boardId);
        Location teamLocation = resolveTeamLocation(author, board);
        postRepositoryPort.save(Post.create(author, board, content, postImage, score, aiReview,
                teamLocation.getLocationName(), teamLocation));

        // 게시물 작성도 활동이므로 lastActiveAt을 갱신하고 휴면이었다면 ACTIVE로 복귀한다
        author.touch();

        // 점수 실시간 가산
        accrueScores(board, teamLocation, author, score, 1);
    }

    private Location resolveTeamLocation(Users author, Board board) {
        Location authorLocation = author.getMainLocation();

        Match match = board.getMatch();
        if (authorLocation.getLocationId().equals(match.getTeam1().getLocationId())) {
            return match.getTeam1();
        }
        if (authorLocation.getLocationId().equals(match.getTeam2().getLocationId())) {
            return match.getTeam2();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 대결에 참가한 동네가 아닙니다.");
    }

    @Override
    @Transactional
    public void updatePost(UUID postId, UUID requesterId, boolean isAdmin, String content) {
        Post post = getPost(postId);
        validateOwner(post, requesterId, isAdmin);
        post.updateContent(content);
    }

    @Override
    @Transactional
    public void deletePost(UUID postId, UUID requesterId, boolean isAdmin) {
        Post post = getPost(postId);
        validateOwner(post, requesterId, isAdmin);
        post.deactivate();

        // 삭제 시 이 게시물이 올렸던 점수를 그대로 뺀다.
        // 작성 시점 스냅샷으로 재계산하므로 인원이 바뀌어도 더했던 값과 같은 값을 뺀다.
        // findActiveById가 is_active인 게시물만 돌려주므로 두 번 차감될 일은 없다.
        if (post.getScore() != null && post.getTeamLocation() != null) {
            accrueScores(post.getBoard(), post.getTeamLocation(), post.getUserId(), post.getScore(), -1);
        }
    }

    /**
     * 점수 누적과 차감의 단일 통로. direction이 1이면 가산, -1이면 차감이다.
     * 지역 점수는 round(AI점수 * K * C / max(n, minTeamSize))로 팀 규모를 보정하고
     * 개인 점수는 round(AI점수 * K)로 보정 없이 계산한다.
     */
    private void accrueScores(Board board, Location teamLocation, Users author, int score, int direction) {
        Match match = board.getMatch();
        Stage stage = match.getStage();

        Double c = stage.getAvgLocationMemberCount();
        if (c == null) {
            // 매치가 있는데 C가 없으면 매칭 배치가 스냅샷을 안 채운 데이터 버그라 바로 예외를 던진다
            throw new IllegalStateException("라운드의 평균 동네 인원(C)이 없습니다. stageId=" + stage.getStageId());
        }

        // n은 작성자 팀의 매칭 시점 인원 스냅샷. BYE 매치는 team1 == team2라 어느 쪽이든 같다
        int n;
        if (teamLocation.getLocationId().equals(match.getTeam1().getLocationId())) {
            n = match.getTeam1MemberCount();
        } else {
            n = match.getTeam2MemberCount();
        }

        double k = rankingProperties.baseMultiplier();
        double adjust = c / Math.max(n, rankingProperties.minTeamSize());
        long teamDelta = Math.round(score * k * adjust) * direction;
        long userDelta = Math.round(score * k) * direction;

        rankingRepositoryPort.addLocationScore(stage.getStageId(), teamLocation.getLocationId(), teamDelta);
        rankingRepositoryPort.addUserScore(stage.getStageId(), author.getUserId(), userDelta);
    }

    private void validateOwner(Post post, UUID requesterId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (!post.getUserId().getUserId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 게시물만 수정/삭제할 수 있습니다.");
        }
    }
}
