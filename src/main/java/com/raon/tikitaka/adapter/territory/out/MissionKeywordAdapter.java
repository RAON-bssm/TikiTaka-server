package com.raon.tikitaka.adapter.territory.out;

import com.raon.tikitaka.adapter.keyword.out.KeywordJpaRepository;
import com.raon.tikitaka.application.territory.out.MissionKeywordPort;
import com.raon.tikitaka.domain.keyword.Keyword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** keyword 테이블에서 형용사·명사를 읽는다. 땅의 미션 칸(10자)을 넘는 키워드는 뺀다 */
@Component
@RequiredArgsConstructor
public class MissionKeywordAdapter implements MissionKeywordPort {

    private static final String TYPE_ADJECTIVE = "형용사";
    private static final String TYPE_NOUN = "명사";
    private static final int MAX_LENGTH = 10;

    private final KeywordJpaRepository keywordJpaRepository;

    @Override
    public List<String> findAdjectives() {
        return find(TYPE_ADJECTIVE);
    }

    @Override
    public List<String> findNouns() {
        return find(TYPE_NOUN);
    }

    private List<String> find(String type) {
        return keywordJpaRepository.findAllByType(type).stream()
                .map(Keyword::getKeyword)
                .filter(k -> k.length() <= MAX_LENGTH)
                .toList();
    }
}
