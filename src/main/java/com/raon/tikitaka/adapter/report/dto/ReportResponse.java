package com.raon.tikitaka.adapter.report.dto;

public record ReportResponse(Long reportId) {

    public static ReportResponse of(Long reportId) {
        return new ReportResponse(reportId);
    }
}
