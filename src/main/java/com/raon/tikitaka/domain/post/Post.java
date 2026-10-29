package com.raon.tikitaka.domain.post;

import com.raon.tikitaka.domain.board.Board;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "post",
        indexes = @Index(name = "idx_post_status_review_next_at", columnList = "status, review_next_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "post_id", columnDefinition = "uuid")
    private UUID postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(name = "content")
    private String content;

    @Column(name = "post_image")
    private String postImage;

    @Column(name = "score")
    private Integer score;

    @Column(name = "ai_review")
    private String aiReview;

    @Column(name = "location")
    private String location;

    /**
     * 작성 시점의 소속 팀 스냅샷. 작성자가 그때 있던 지역이고 점수 집계에 쓴다.
     * 문자열 location 필드는 표시용으로 유지하고 집계는 이 FK로만 한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location teamLocation;

    /**
     * 작성 시점에 지역 점수까지 올렸는지 여부. 본진에서 쓴 글만 true다.
     * 삭제 시 차감 여부를 이 스냅샷으로 판정한다. 나중에 작성자가 이사를 가도
     * 올렸던 만큼만 정확히 되돌리기 위해서다.
     */
    @Column(name = "location_scored", nullable = false)
    @ColumnDefault("false")
    private boolean locationScored;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive;

    /**
     * 심사 상태. 기존 글은 마이그레이션에서 DONE으로 채운다.
     * 동기 방식으로 쓴 글은 바로 DONE, 비동기 방식으로 쓴 글은 PENDING으로 시작한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'DONE'")
    private PostStatus status;

    /** 실제 AI 호출이 실패한 횟수. 대기열 대기나 속도 제한 대기로는 늘지 않는다 */
    @Column(name = "review_attempts", nullable = false)
    @ColumnDefault("0")
    private int reviewAttempts;

    /** 재시도 백오프. 이 시각 전에는 다시 선점하지 않는다. null이면 바로 선점 가능 */
    @Column(name = "review_next_at")
    private LocalDateTime reviewNextAt;

    /** 워커가 선점한 시각. 서버가 심사 도중 죽어도 일정 시간이 지나면 다시 선점된다 */
    @Column(name = "review_started_at")
    private LocalDateTime reviewStartedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static Post create(Users author, Board board, String content, String postImage, Integer score, String aiReview, String location, Location teamLocation, boolean locationScored) {
        Post post = new Post();
        post.userId = author;
        post.board = board;
        post.content = content;
        post.postImage = postImage;
        post.score = score;
        post.aiReview = aiReview;
        post.location = location;
        post.teamLocation = teamLocation;
        post.locationScored = locationScored;
        post.isActive = true;
        post.status = PostStatus.DONE;
        post.createdAt = LocalDateTime.now();
        post.updatedAt = LocalDateTime.now();
        return post;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public boolean isDone() {
        return status == PostStatus.DONE;
    }

    public boolean isPending() {
        return status == PostStatus.PENDING;
    }
}
