package com.raon.tikitaka.application.territory.out;

import java.util.List;

/** 금칙어·예외 단어 목록. 운영 중 DB에 바로 추가할 수 있다 */
public interface WordFilterPort {

    List<String> findAllBannedWords();

    List<String> findAllAllowedWords();
}
