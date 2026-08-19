package com.raon.tikitaka.application.match.out;

import java.util.List;

public interface KeywordRepositoryPort {

    /**
     * type = '명사' 또는 '형용사' 인 키워드 문자열 목록.
     */
    List<String> findKeywordsByType(String type);
}
