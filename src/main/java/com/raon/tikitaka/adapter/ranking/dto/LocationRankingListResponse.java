package com.raon.tikitaka.adapter.ranking.dto;

import com.raon.tikitaka.application.ranking.LocationRankRow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record LocationRankingListResponse(LocalDateTime updatedAt, List<LocationRankingResponse> locationRanking) {

    public static LocationRankingListResponse from(List<LocationRankRow> rows) {
        List<LocationRankingResponse> ranking = new ArrayList<>();
        for (LocationRankRow row : rows) {
            ranking.add(LocationRankingResponse.from(row));
        }
        return new LocationRankingListResponse(LocalDateTime.now(), ranking);
    }
}
