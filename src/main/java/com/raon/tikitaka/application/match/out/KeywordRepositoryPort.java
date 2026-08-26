package com.raon.tikitaka.application.match.out;

import com.raon.tikitaka.domain.keyword.Keyword;

import java.util.List;

public interface KeywordRepositoryPort {

    /**
     * type = '명사' 또는 '형용사' 인 키워드 문자열 목록.
     */
    List<String> findKeywordsByType(String type);

    // ===== 키워드 관리 (admin) =====

    List<Keyword> findAll();

    boolean exists(String keyword);

    Keyword save(Keyword keyword);

    void delete(String keyword);
}
