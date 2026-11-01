package com.raon.tikitaka.application.territory.policy;

/**
 * 욕설 변형을 같은 글자로 모으는 한글 정규화.
 * 완성형 한글 = 0xAC00 + (초성 × 21 + 중성) × 28 + 종성 이라는 유니코드 규칙으로 자모를 분해한다.
 *
 * - skeleton(뼈대): 받침 제거 + 쌍자음→평음 + 비슷한 모음 통합. 시발·씨발·싯발·씨바·싯바 → "시바"
 * - hangulOnly: 한글만 남긴 원문. 한 글자 금칙어용(뼈대로 바꾸면 '좆'이 '조'가 되어 '조개'까지, '씹'이 '시'가 되어 '시계'까지 걸린다)
 * 한글이 아닌 글자(공백·숫자·기호)는 버린다. "시 발", "시1발"도 "시발"로 본다.
 */
public final class HangulNormalizer {

    private static final int BASE = 0xAC00;
    private static final int LAST = 0xD7A3;
    // 초성 19개 중 쌍자음 → 평음: ㄲ→ㄱ, ㄸ→ㄷ, ㅃ→ㅂ, ㅆ→ㅅ, ㅉ→ㅈ
    private static final int[] CHO = {0, 0, 2, 3, 3, 5, 6, 7, 7, 9, 9, 11, 12, 12, 14, 15, 16, 17, 18};
    // 중성 21개: ㅔ→ㅐ, ㅖ→ㅒ, ㅙ·ㅞ→ㅚ, ㅢ→ㅣ
    private static final int[] JUNG = {0, 1, 2, 3, 4, 1, 6, 3, 8, 9, 11, 11, 12, 13, 14, 11, 16, 17, 18, 20, 20};

    private HangulNormalizer() {
    }

    public static String skeleton(String text) {
        return normalize(text, false);
    }

    public static String hangulOnly(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= BASE && c <= LAST) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalize(String text, boolean keepFinal) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c < BASE || c > LAST) {
                continue;
            }
            int offset = c - BASE;
            int cho = offset / (21 * 28);
            int jung = (offset % (21 * 28)) / 28;
            int jong = keepFinal ? offset % 28 : 0;
            sb.append((char) (BASE + (CHO[cho] * 21 + JUNG[jung]) * 28 + jong));
        }
        return sb.toString();
    }
}
