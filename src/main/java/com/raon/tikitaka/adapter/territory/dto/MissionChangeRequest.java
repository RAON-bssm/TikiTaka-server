package com.raon.tikitaka.adapter.territory.dto;

/** JSON 본문. 응답과 같이 snake_case(scoring_criteria)로 받는다 */
public record MissionChangeRequest(String adjective, String noun, String scoringCriteria) {
}
