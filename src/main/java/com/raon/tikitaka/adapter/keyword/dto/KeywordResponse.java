package com.raon.tikitaka.adapter.keyword.dto;

import com.raon.tikitaka.domain.keyword.Keyword;

public record KeywordResponse(String keyword, String type) {

    public static KeywordResponse from(Keyword entity) {
        return new KeywordResponse(entity.getKeyword(), entity.getType());
    }
}
