package com.raon.tikitaka.application.storage;

import com.raon.tikitaka.application.storage.out.StoragePort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StorageServiceTest {

    private final StoragePort storagePort = mock(StoragePort.class);
    private final StorageService service = new StorageService(storagePort);

    @Test
    void 이미지_정리는_스토리지에_삭제를_요청한다() {
        service.deleteImageQuietly("some-key.jpg");

        verify(storagePort).delete("some-key.jpg");
    }

    @Test
    void 삭제가_실패해도_예외를_던지지_않아_원래_실패_사유를_가리지_않는다() {
        doThrow(new IllegalStateException("AccessDenied")).when(storagePort).delete("some-key.jpg");

        assertThatCode(() -> service.deleteImageQuietly("some-key.jpg")).doesNotThrowAnyException();
    }
}
