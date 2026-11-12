package com.raon.tikitaka.application.storage;

/**
 * 원본과 함께 올린 썸네일의 S3 key. thumbnailKey는 썸네일을 만들지 못했거나 올리지 못했으면 null이다.
 */
public record UploadedImage(String key, String thumbnailKey) {
}
