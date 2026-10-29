package com.raon.tikitaka.adapter.storage.out;

import com.raon.tikitaka.application.storage.StoredImage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * presigned GET URL로 이미지를 내려받는지 본다. S3 대신 로컬 HTTP 서버가 응답한다.
 */
class S3AdapterDownloadTest {

    private HttpServer server;
    private final S3Presigner presigner = mock(S3Presigner.class);
    private S3Adapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        adapter = new S3Adapter(presigner);
        ReflectionTestUtils.setField(adapter, "bucket", "test-bucket");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 이미지_바이트와_content_type을_돌려준다() throws Exception {
        // WebClient 기본 버퍼(256KB)를 넘는 크기로 버퍼 설정까지 확인한다
        byte[] image = new byte[2 * 1024 * 1024];
        Arrays.fill(image, (byte) 7);
        server.createContext("/photo.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, image.length);
            exchange.getResponseBody().write(image);
            exchange.close();
        });
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/photo.jpg").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        StoredImage stored = adapter.download("photo.jpg");

        assertThat(stored.content()).isEqualTo(image);
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void 응답이_2xx가_아니면_S3_오류_본문을_이미지로_쓰지_않고_실패한다() throws Exception {
        // 리전이 다르면 S3가 301 PermanentRedirect와 오류 XML을 준다. WebClient는 3xx를 예외로 바꾸지 않는다
        byte[] errorXml = "<Error><Code>PermanentRedirect</Code></Error>".getBytes();
        server.createContext("/moved.jpg", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(301, errorXml.length);
            exchange.getResponseBody().write(errorXml);
            exchange.close();
        });
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/moved.jpg").toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        assertThatThrownBy(() -> adapter.download("moved.jpg")).isInstanceOf(IllegalStateException.class);
    }
}
