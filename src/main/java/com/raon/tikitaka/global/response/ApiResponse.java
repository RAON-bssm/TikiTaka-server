package com.raon.tikitaka.global.response;

public record ApiResponse<T>(int status, String message, T data) {

    public static <T> ApiResponse<T> of(int status, String message, T data) {
        return new ApiResponse<>(status, message, data);
    }
}
