package com.raon.tikitaka.adapter.keyword.out;

import com.raon.tikitaka.application.match.out.KeywordRepositoryPort;
import com.raon.tikitaka.domain.keyword.Keyword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KeywordPersistenceAdapter implements KeywordRepositoryPort {

    private final KeywordJpaRepository keywordJpaRepository;

    @Override
    public List<String> findKeywordsByType(String type) {
        List<Keyword> keywords = keywordJpaRepository.findAllByType(type);
        List<String> result = new ArrayList<>();
        for (Keyword keyword : keywords) {
            result.add(keyword.getKeyword());
        }
        return result;
    }
}
