package com.raon.tikitaka.global.exception;

import com.raon.tikitaka.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(SocialLoginFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocialLoginFailed(SocialLoginFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.of(401, e.getMessage(), null));
    }
}
