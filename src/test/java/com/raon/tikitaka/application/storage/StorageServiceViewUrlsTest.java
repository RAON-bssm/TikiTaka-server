package com.raon.tikitaka.application.storage;

import com.raon.tikitaka.application.storage.out.StoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 일괄 조회 URL 발급. key마다 단건과 같은 유효 시간으로 서명하고, 요청 순서를 지키며 중복은 한 번만 발급한다.
 */
class StorageServiceViewUrlsTest {

    private final StoragePort storagePort = mock(StoragePort.class);
    private final StorageService storageService = new StorageService(storagePort);

    @BeforeEach
    void setUp() {
        when(storagePort.issueViewUrl(anyString(), any())).thenAnswer(inv -> url(inv.getArgument(0)));
    }

    @Test
    void key마다_URL을_만들고_요청_순서를_지킨다() {
        List<ViewUrl> result = storageService.issueViewUrls(List.of("c.jpg", "a.jpg", "dir/b.jpg"));

        assertThat(result).extracting(ViewUrl::key).containsExactly("c.jpg", "a.jpg", "dir/b.jpg");
        assertThat(result).extracting(v -> v.url().toString()).containsExactly(
                "https://bucket.s3.amazonaws.com/c.jpg?sig=1",
                "https://bucket.s3.amazonaws.com/a.jpg?sig=1",
                "https://bucket.s3.amazonaws.com/dir/b.jpg?sig=1");
    }

    @Test
    void 중복_key는_처음_나온_자리에_한_번만_발급한다() {
        List<ViewUrl> result = storageService.issueViewUrls(List.of("a.jpg", "b.jpg", "a.jpg", "b.jpg", "c.jpg"));

        assertThat(result).extracting(ViewUrl::key).containsExactly("a.jpg", "b.jpg", "c.jpg");
        verify(storagePort, times(1)).issueViewUrl(eq("a.jpg"), any());
        verify(storagePort, times(1)).issueViewUrl(eq("b.jpg"), any());
    }

    @Test
    void 단건과_같은_10분_유효_시간과_같은_URL을_쓴다() {
        URL single = storageService.issueViewUrl("a.jpg");
        List<ViewUrl> batch = storageService.issueViewUrls(List.of("a.jpg"));

        assertThat(batch.get(0).url()).isEqualTo(single);
        verify(storagePort, times(2)).issueViewUrl("a.jpg", Duration.ofMinutes(10));
    }

    @Test
    void key_50개는_발급한다() {
        assertThat(storageService.issueViewUrls(keys(50))).hasSize(50);
    }

    @Test
    void key가_없거나_50개를_넘으면_서명하지_않고_400() {
        assertBadRequest(() -> storageService.issueViewUrls(List.of()), "key는 1개 이상 50개 이하여야 합니다.");
        assertBadRequest(() -> storageService.issueViewUrls(keys(51)), "key는 1개 이상 50개 이하여야 합니다.");
        verify(storagePort, never()).issueViewUrl(anyString(), any());
    }

    @Test
    void 빈_key나_공백뿐인_key가_섞이면_서명하지_않고_400() {
        assertBadRequest(() -> storageService.issueViewUrls(List.of("a.jpg", "")), "빈 key는 보낼 수 없습니다.");
        assertBadRequest(() -> storageService.issueViewUrls(List.of("a.jpg", "   ")), "빈 key는 보낼 수 없습니다.");
        assertBadRequest(() -> storageService.issueViewUrls(Arrays.asList("a.jpg", null)), "빈 key는 보낼 수 없습니다.");
        verify(storagePort, never()).issueViewUrl(anyString(), any());
    }

    // ===== helpers =====

    private static List<String> keys(int count) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add("k" + i + ".jpg");
        }
        return keys;
    }

    private static URL url(String key) throws MalformedURLException {
        return URI.create("https://bucket.s3.amazonaws.com/" + key + "?sig=1").toURL();
    }

    private static void assertBadRequest(Runnable call, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }
}
