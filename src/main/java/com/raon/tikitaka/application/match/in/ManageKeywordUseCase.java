package com.raon.tikitaka.application.match.in;

import com.raon.tikitaka.domain.keyword.Keyword;

import java.util.List;

/**
 * 미션 키워드 관리. 관리자 전용이다.
 * 추가와 삭제는 다음 매치 생성부터 반영되고 이미 만들어진 미션은 바뀌지 않는다.
 */
public interface ManageKeywordUseCase {

    List<Keyword> getKeywords();

    void addKeyword(String keyword, String type);

    void removeKeyword(String keyword);
}
