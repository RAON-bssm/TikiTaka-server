package com.raon.tikitaka.application.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 글 목록 커서의 인코딩과 해석. 왕복해도 마이크로초가 그대로 남아야 하고, 서버가 만든 값이 아니면 400이다.
 */
class PostCursorTest {

    @Test
    void 왕복하면_마이크로초까지_그대로다() {
        PostCursor cursor = new PostCursor(LocalDateTime.of(2026, 11, 10, 10, 0, 0, 123_456_000), UUID.randomUUID());

        PostCursor decoded = PostCursor.decode(cursor.encode());

        assertThat(decoded).isEqualTo(cursor);
        assertThat(decoded.createdAt().getNano()).isEqualTo(123_456_000);
    }

    @Test
    void 마이크로초만_다른_두_값은_서로_다른_커서가_된다() {
        UUID postId = UUID.randomUUID();
        PostCursor a = new PostCursor(LocalDateTime.of(2026, 11, 10, 10, 0, 0, 1_000), postId);
        PostCursor b = new PostCursor(LocalDateTime.of(2026, 11, 10, 10, 0, 0, 2_000), postId);

        assertThat(a.encode()).isNotEqualTo(b.encode());
        assertThat(PostCursor.decode(a.encode()).createdAt()).isEqualTo(a.createdAt());
        assertThat(PostCursor.decode(b.encode()).createdAt()).isEqualTo(b.createdAt());
    }

    @Test
    void 초와_소수점이_0이어도_왕복한다() {
        PostCursor cursor = new PostCursor(LocalDateTime.of(2026, 11, 10, 10, 0), UUID.randomUUID());

        assertThat(PostCursor.decode(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void URL에_그대로_넣을_수_있는_문자만_쓴다() {
        PostCursor cursor = new PostCursor(LocalDateTime.of(2026, 11, 10, 10, 0, 0, 999_999_000), UUID.randomUUID());

        assertThat(cursor.encode()).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!bad", "%%%", "aGVsbG8", "a"})
    void 깨진_문자열은_400(String value) {
        assertInvalid(value);
    }

    @Test
    void 구분자가_없으면_400() {
        assertInvalid(encodeRaw("2026-11-10T10:00:00.000001" + UUID.randomUUID()));
    }

    @Test
    void 시각_형식이_다르면_400() {
        assertInvalid(encodeRaw("2026/11/10 10:00|" + UUID.randomUUID()));
        assertInvalid(encodeRaw("1731200000000|" + UUID.randomUUID()));
    }

    @Test
    void post_id가_UUID가_아니면_400() {
        assertInvalid(encodeRaw("2026-11-10T10:00:00.000001|not-a-uuid"));
        assertInvalid(encodeRaw("2026-11-10T10:00:00.000001|"));
    }

    // ===== helpers =====

    private static String encodeRaw(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalid(String value) {
        assertThatThrownBy(() -> PostCursor.decode(value))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("잘못된 cursor입니다.");
                });
    }
}
