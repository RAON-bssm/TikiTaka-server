package com.raon.tikitaka.adapter.match.out;

import com.raon.tikitaka.application.match.out.MissionGeneratorPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 외부 API 없이 키워드를 '형용사 명사' 형태로 이어 붙여 미션 문장을 만든다.
 * 기본 구현체이며 mission.generator=gemini 로 설정하면 GeminiMissionAdapter로 바뀐다.
 */
@Component
@ConditionalOnProperty(prefix = "mission", name = "generator", havingValue = "keyword", matchIfMissing = true)
public class KeywordMissionAdapter implements MissionGeneratorPort {

    @Override
    public String generate(String adjective, String noun) {
        return adjective.trim() + " " + noun.trim();
    }
}
