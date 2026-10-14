package com.raon.tikitaka.adapter.report.out;

import com.raon.tikitaka.domain.report.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportJpaRepository extends JpaRepository<Report, Long> {

    boolean existsByReporter_UserIdAndPost_PostId(UUID reporterId, UUID postId);

    boolean existsByReporter_UserIdAndReportedUser_UserId(UUID reporterId, UUID reportedUserId);
}
