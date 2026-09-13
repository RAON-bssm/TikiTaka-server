package com.raon.tikitaka.adapter.ranking.dto;

import com.raon.tikitaka.application.ranking.LocationRankRow;

/**
 * 지역 랭킹 응답 한 건. 필드는 API 명세서를 따른다.
 */
public record LocationRankingResponse(Long locationId, String cityName, String locationName, Integer locationRank,
                                      Long locationScore) {

    public static LocationRankingResponse from(LocationRankRow row) {
        return new LocationRankingResponse(row.getLocationId(), row.getCityName(), row.getLocationName(),
                row.getLocationRank(), row.getLocationScore());
    }
}
