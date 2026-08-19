package com.raon.tikitaka.adapter.match.dto;

import com.raon.tikitaka.application.match.MatchResult;

import java.util.ArrayList;
import java.util.List;

public record MatchResultListResponse(List<MatchResultResponse> match) {

    public static MatchResultListResponse from(List<MatchResult> results) {
        List<MatchResultResponse> match = new ArrayList<>();
        for (MatchResult result : results) {
            match.add(MatchResultResponse.from(result));
        }
        return new MatchResultListResponse(match);
    }
}
