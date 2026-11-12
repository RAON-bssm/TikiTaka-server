package com.raon.tikitaka.application.storage;

import com.raon.tikitaka.application.storage.out.StoragePort;
import com.raon.tikitaka.application.storage.out.ThumbnailGeneratorPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 원본과 썸네일 업로드. 썸네일은 만들거나 올리지 못해도 업로드 전체를 실패시키지 않는다.
 */
class StorageServiceThumbnailTest {

    private final StoragePort storagePort = mock(StoragePort.class);
    private final ThumbnailGeneratorPort thumbnailGeneratorPort = mock(ThumbnailGeneratorPort.class);
    private final StorageService storageService = new StorageService(storagePort, thumbnailGeneratorPort);

    private final byte[] original = {1, 2, 3};
    private final byte[] thumbnail = {9, 8};

    @Test
    void 원본을_먼저_올리고_썸네일을_JPEG로_올린_뒤_두_key를_돌려준다() {
        when(thumbnailGeneratorPort.createThumbnail(original)).thenReturn(Optional.of(thumbnail));

        UploadedImage result = storageService.uploadImageWithThumbnail(original, "photo.png", "image/png");

        assertThat(result.key()).endsWith("-photo.png");
        assertThat(result.thumbnailKey()).matches("thumbnails/[0-9a-f-]{36}\\.jpg");
        // 썸네일 key에는 사용자가 정한 파일명이 들어가지 않는다
        assertThat(result.thumbnailKey()).doesNotContain("photo");
        InOrder order = inOrder(storagePort, thumbnailGeneratorPort);
        order.verify(storagePort).upload(result.key(), original, "image/png");
        order.verify(thumbnailGeneratorPort).createThumbnail(original);
        order.verify(storagePort).upload(result.thumbnailKey(), thumbnail, "image/jpeg");
    }

    @Test
    void 썸네일을_만들지_못하면_원본만_올리고_thumbnailKey는_null이다() {
        when(thumbnailGeneratorPort.createThumbnail(original)).thenReturn(Optional.empty());

        UploadedImage result = storageService.uploadImageWithThumbnail(original, "photo.heic", "image/heic");

        assertThat(result.key()).endsWith("-photo.heic");
        assertThat(result.thumbnailKey()).isNull();
        verify(storagePort, times(1)).upload(any(), any(), any());
    }

    @Test
    void 썸네일_업로드가_실패해도_원본_key는_돌려준다() {
        when(thumbnailGeneratorPort.createThumbnail(original)).thenReturn(Optional.of(thumbnail));
        doThrow(new IllegalStateException("S3 오류")).when(storagePort).upload(startsWith("thumbnails/"), any(), any());

        UploadedImage result = storageService.uploadImageWithThumbnail(original, "photo.jpg", "image/jpeg");

        assertThat(result.key()).endsWith("-photo.jpg");
        assertThat(result.thumbnailKey()).isNull();
    }

    @Test
    void 썸네일_생성기가_예외를_던져도_원본_key는_돌려준다() {
        when(thumbnailGeneratorPort.createThumbnail(original)).thenThrow(new IllegalStateException("예상 못 한 오류"));

        UploadedImage result = storageService.uploadImageWithThumbnail(original, "photo.jpg", "image/jpeg");

        assertThat(result.key()).endsWith("-photo.jpg");
        assertThat(result.thumbnailKey()).isNull();
    }

    @Test
    void 원본_업로드가_실패하면_예외이고_썸네일은_만들지_않는다() {
        doThrow(new IllegalStateException("S3 오류")).when(storagePort).upload(endsWith("-photo.jpg"), eq(original), any());

        assertThatThrownBy(() -> storageService.uploadImageWithThumbnail(original, "photo.jpg", "image/jpeg"))
                .isInstanceOf(IllegalStateException.class);
        verify(thumbnailGeneratorPort, never()).createThumbnail(any());
    }
}
