package com.raon.tikitaka.adapter.post.dto;

import com.raon.tikitaka.domain.match.Match;
import com.raon.tikitaka.domain.post.Post;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 마이페이지 "게시물 보관함"용 응답. 여러 라운드·게시판을 넘나드는 목록이라
 * 게시물 정보와 함께 그 글이 속했던 게시판·미션 정보도 같이 내려준다.
 * 항상 호출자 본인의 글만 담기므로 user_id·user_name은 넣지 않는다.
 */
public record MyPostSummaryResponse(
        UUID postId,
        String postImage,
        Integer score,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String content,
        String cityName,
        String location,
        Long boardId,
        String mission,
        String team1Name,
        String team2Name,
        String matchType,
        Integer season,
        Integer round
) {

    public static MyPostSummaryResponse from(Post post) {
        Match match = post.getBoard().getMatch();
        return new MyPostSummaryResponse(
                post.getPostId(),
                post.getPostImage(),
                post.getScore(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getContent(),
                post.getTeamLocation().getCityName(),
                post.getLocation(),
                post.getBoard().getBoardId(),
                match.getMission(),
                match.getTeam1().getFullName(),
                match.getTeam2().getFullName(),
                match.getMatchType().getDescription(),
                match.getStage().getSeason(),
                match.getStage().getRound()
        );
    }
}
