package com.raon.tikitaka.application.match;

import com.raon.tikitaka.application.match.in.ManageKeywordUseCase;
import com.raon.tikitaka.application.match.out.KeywordRepositoryPort;
import com.raon.tikitaka.domain.keyword.Keyword;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class KeywordService implements ManageKeywordUseCase {

    // 미션 생성기가 이 두 타입을 조합하므로 다른 값이 들어가면 안 된다
    private static final String TYPE_NOUN = "명사";
    private static final String TYPE_ADJECTIVE = "형용사";

    private final KeywordRepositoryPort keywordRepositoryPort;

    @Override
    public List<Keyword> getKeywords() {
        return keywordRepositoryPort.findAll();
    }

    @Override
    public void addKeyword(String keyword, String type) {
        if (keyword == null || keyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "키워드를 입력해주세요.");
        }
        if (!TYPE_NOUN.equals(type) && !TYPE_ADJECTIVE.equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type은 '명사' 또는 '형용사'여야 합니다.");
        }
        if (keywordRepositoryPort.exists(keyword)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 키워드입니다.");
        }
        keywordRepositoryPort.save(Keyword.create(keyword.trim(), type));
    }

    @Override
    public void removeKeyword(String keyword) {
        if (!keywordRepositoryPort.exists(keyword)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 키워드입니다.");
        }
        keywordRepositoryPort.delete(keyword);
    }
}
