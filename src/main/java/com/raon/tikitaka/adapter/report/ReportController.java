package com.raon.tikitaka.adapter.report;

import com.raon.tikitaka.adapter.report.dto.ReportRequest;
import com.raon.tikitaka.adapter.report.dto.ReportResponse;
import com.raon.tikitaka.application.report.in.ReportPostUseCase;
import com.raon.tikitaka.application.report.in.ReportUserUseCase;
import com.raon.tikitaka.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 게시물·유저 신고 API. SecurityConfig는 /api/post/**의 GET만 열어두므로
 * 두 엔드포인트 모두 POST라 인증이 필요하다.
 */
@RestController
@RequiredArgsConstructor
public class ReportController {

    private static final String REPORTED_MESSAGE = "신고가 접수되었습니다";

    private final ReportPostUseCase reportPostUseCase;
    private final ReportUserUseCase reportUserUseCase;

    @PostMapping("/api/post/{postId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> reportPost(@AuthenticationPrincipal UUID reporterId,
                                                  @PathVariable UUID postId,
                                                  @RequestBody ReportRequest request) {
        Long reportId = reportPostUseCase.reportPost(reporterId, postId, request.toReason(), request.detail());
        return ApiResponse.of(201, REPORTED_MESSAGE, ReportResponse.of(reportId));
    }

    @PostMapping("/api/user/{userId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> reportUser(@AuthenticationPrincipal UUID reporterId,
                                                  @PathVariable UUID userId,
                                                  @RequestBody ReportRequest request) {
        Long reportId = reportUserUseCase.reportUser(reporterId, userId, request.toReason(), request.detail());
        return ApiResponse.of(201, REPORTED_MESSAGE, ReportResponse.of(reportId));
    }
}
