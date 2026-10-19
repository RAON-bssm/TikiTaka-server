package com.raon.tikitaka.application.block;

/**
 * 동시 요청이 같은 차단을 먼저 만들어 유니크 제약에 걸린 경우. 차단은 idempotent라 실패가 아니고,
 * 트랜잭션만 롤백시킨 뒤 컨트롤러가 성공으로 응답한다.
 */
public class AlreadyBlockedException extends RuntimeException {

    public AlreadyBlockedException() {
        super("이미 차단한 유저입니다.");
    }
}
