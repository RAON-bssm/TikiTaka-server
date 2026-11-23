package com.raon.tikitaka.application.push.out;

import com.raon.tikitaka.application.push.PushMessage;
import com.raon.tikitaka.application.push.PushSendResult;

import java.util.List;

public interface PushSenderPort {

    /** 토큰들에 같은 메시지를 보낸다. 실패해도 예외를 던지지 않고 결과에 담는다 */
    PushSendResult send(List<String> tokens, PushMessage message);
}
