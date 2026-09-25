package com.raon.tikitaka.adapter.chat.dto;

import com.raon.tikitaka.application.chat.ChatSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 답변의 근거가 된 동네 정보. 벡터DB를 붙이기 전까지는 빈 배열이다.
 * 나중에 채워질 자리를 지금부터 계약에 넣어둬야 앱을 다시 고치지 않는다.
 */
public record ChatSourceResponse(String type, String id, String title) {

    public static List<ChatSourceResponse> from(List<ChatSource> sources) {
        List<ChatSourceResponse> responses = new ArrayList<>();
        if (sources == null) {
            return responses;
        }
        for (ChatSource source : sources) {
            responses.add(new ChatSourceResponse(source.type(), source.id(), source.title()));
        }
        return responses;
    }
}
