package com.raon.tikitaka.global.exception;

import com.raon.tikitaka.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 서비스 계층이 던지는 ResponseStatusException을 공통 응답 형식으로 바꾼다.
     * 이 핸들러가 없으면 스프링 기본 에러 형식으로 내려가 메시지가 클라이언트에 전달되지 않는다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason();
        if (message == null) {
            message = "요청을 처리할 수 없습니다.";
        }
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.of(e.getStatusCode().value(), message, null));
    }

    @ExceptionHandler(InsufficientPointException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientPoint(InsufficientPointException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.of(403, e.getMessage(), null));
    }

    @ExceptionHandler(SubLocationNotSetException.class)
    public ResponseEntity<ApiResponse<Void>> handleSubLocationNotSet(SubLocationNotSetException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.of(400, e.getMessage(), null));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.of(401, e.getMessage(), null));
    }

    @ExceptionHandler(DuplicateUserNameException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateUserName(DuplicateUserNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.of(409, e.getMessage(), null));
    }

    @ExceptionHandler(AlreadyOwnedProductException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyOwnedProduct(AlreadyOwnedProductException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.of(409, e.getMessage(), null));
    }

    @ExceptionHandler(SocialLoginFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocialLoginFailed(SocialLoginFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.of(401, e.getMessage(), null));
    }
}
