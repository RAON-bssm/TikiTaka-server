package com.raon.tikitaka.domain.report;

import com.raon.tikitaka.domain.enums.ReportReason;
import com.raon.tikitaka.domain.post.Post;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게시물 신고와 유저 신고를 한 테이블에 담는다. post와 reportedUser 중 정확히 하나만 값이 있고
 * 팩토리 메서드가 그걸 보장한다. DB 체크 제약은 직접 INSERT하는 경우까지 막는 최후 방어선이다.
 *
 * 유니크 제약은 동시 요청의 이중 신고를 막는 용도다. 정상 흐름은 서비스가 먼저 존재 여부를 보고 409로 끊는다.
 * PostgreSQL은 NULL끼리를 서로 다르게 보므로, 유저 신고 행(post_id가 null)끼리는 게시물 쪽 제약에 걸리지 않는다.
 */
@Entity
@Table(name = "report",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_report_reporter_post", columnNames = {"reporter_id", "post_id"}),
                @UniqueConstraint(name = "uk_report_reporter_user", columnNames = {"reporter_id", "reported_user_id"})
        },
        check = @CheckConstraint(name = "ck_report_single_target",
                constraint = "(post_id is null) <> (reported_user_id is null)"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Users reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_user_id")
    private Users reportedUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private ReportReason reason;

    @Column(name = "detail", length = 200)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Report ofPost(Users reporter, Post post, ReportReason reason, String detail) {
        Report report = create(reporter, reason, detail);
        report.post = post;
        return report;
    }

    public static Report ofUser(Users reporter, Users reportedUser, ReportReason reason, String detail) {
        Report report = create(reporter, reason, detail);
        report.reportedUser = reportedUser;
        return report;
    }

    private static Report create(Users reporter, ReportReason reason, String detail) {
        Report report = new Report();
        report.reporter = reporter;
        report.reason = reason;
        report.detail = detail;
        report.createdAt = LocalDateTime.now();
        return report;
    }
}
