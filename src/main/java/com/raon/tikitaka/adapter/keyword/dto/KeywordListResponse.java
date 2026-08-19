package com.raon.tikitaka.adapter.keyword.dto;

import com.raon.tikitaka.domain.keyword.Keyword;

import java.util.ArrayList;
import java.util.List;

public record KeywordListResponse(List<KeywordResponse> keywords) {

    public static KeywordListResponse from(List<Keyword> entities) {
        List<KeywordResponse> keywords = new ArrayList<>();
        for (Keyword entity : entities) {
            keywords.add(KeywordResponse.from(entity));
        }
        return new KeywordListResponse(keywords);
    }
}
