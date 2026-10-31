package com.raon.tikitaka.application.territory.out;

/**
 * 땅따먹기 사진 채점. 채점 기준이 부적절하거나 채점을 조작하려는 문장이면
 * 같은 호출에서 criteriaViolation=true, score=0을 돌려준다(추가 AI 호출 없음).
 */
public interface TerritoryScoringPort {

    /** score가 null이면 AI 응답이 깨진 것이다 */
    record Result(Integer score, String review, boolean criteriaViolation, String violationReason) {
    }

    Result score(String adjective, String noun, String criteria, byte[] image, String contentType);
}
