package com.raon.tikitaka.application.storage;

/**
 * 저장소에서 읽어온 이미지. contentType은 저장할 때 붙인 값이고 없으면 null이다.
 */
public record StoredImage(byte[] content, String contentType) {
}
