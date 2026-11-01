package com.raon.tikitaka.application.territory.policy;

import com.raon.tikitaka.application.territory.out.WordFilterPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 미션 입력 검사. Gemini를 부르기 전에 서버에서 거를 수 있는 것은 모두 여기서 거른다.
 * - 형용사·명사 키워드: 완성형 한글 1~10자, 공백 불가 (자음만 쓰기·숫자·기호 끼워 넣기 차단)
 * - 채점 기준: 최대 50자
 * - 셋 모두 금칙어 검사. 채점 기준의 부적절함·프롬프트 인젝션은 채점 호출에서 함께 판정한다
 */
@Component
@RequiredArgsConstructor
public class MissionTextPolicy {

    private static final Pattern KEYWORD = Pattern.compile("^[가-힣]{1,10}$");
    private static final int CRITERIA_MAX = 50;

    private final WordFilterPort wordFilterPort;

    public void validate(String adjective, String noun, String criteria) {
        if (adjective == null || !KEYWORD.matcher(adjective).matches()
                || noun == null || !KEYWORD.matcher(noun).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "미션 키워드는 공백 없이 한글 1~10자로 입력해 주세요.");
        }
        if (criteria != null && criteria.length() > CRITERIA_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "채점 기준은 50자까지 입력할 수 있어요.");
        }
        List<String> banned = wordFilterPort.findAllBannedWords();
        List<String> allowed = wordFilterPort.findAllAllowedWords();
        for (String text : new String[]{adjective, noun, criteria}) {
            if (text != null && containsBannedWord(text, banned, allowed)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용할 수 없는 단어가 들어 있어요.");
            }
        }
    }

    /**
     * 예외 단어를 먼저 지운 뒤 비교한다. 두 글자 이상 금칙어는 뼈대 포함 여부,
     * 한 글자 금칙어는 원래 글자 그대로의 포함 여부로 본다(오탐 방지).
     */
    static boolean containsBannedWord(String text, List<String> banned, List<String> allowed) {
        String rest = text;
        for (String ok : allowed) {
            rest = rest.replace(ok, " ");
        }
        String skeleton = HangulNormalizer.skeleton(rest);
        String raw = HangulNormalizer.hangulOnly(rest);
        for (String word : banned) {
            String target = HangulNormalizer.hangulOnly(word);
            if (target.isEmpty()) {
                continue;
            }
            boolean hit = target.length() == 1
                    ? raw.contains(target)
                    : skeleton.contains(HangulNormalizer.skeleton(target));
            if (hit) {
                return true;
            }
        }
        return false;
    }
}
