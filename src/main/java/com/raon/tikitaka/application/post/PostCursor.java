package com.raon.tikitaka.application.post;

import com.raon.tikitaka.domain.post.Post;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * 글 목록 페이징 커서. 이 페이지 마지막 글의 (createdAt, postId)이고, 다음 페이지는 이 값보다 뒤(오래된 쪽)의 글이다.
 * 클라이언트에는 불투명한 문자열로 내려간다. 형식은 "createdAt|postId"를 base64url(패딩 없음)로 감싼 것이고,
 * createdAt은 ISO 형식이라 PostgreSQL의 마이크로초까지 그대로 남는다. 밀리초로 줄이면 같은 밀리초의 글이 빠지거나 겹친다.
 */
public record PostCursor(LocalDateTime createdAt, UUID postId) {

    private static final String SEPARATOR = "|";
    private static final String INVALID_MESSAGE = "잘못된 cursor입니다.";

    public static PostCursor of(Post post) {
        return new PostCursor(post.getCreatedAt(), post.getPostId());
    }

    public String encode() {
        String raw = createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + SEPARATOR + postId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 서버가 내려준 값이 아니면(깨진 base64, 형식이 다른 값) 400이다.
     */
    public static PostCursor decode(String value) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = raw.indexOf(SEPARATOR);
            if (separator < 0) {
                throw invalid();
            }
            LocalDateTime createdAt = LocalDateTime.parse(raw.substring(0, separator), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            UUID postId = UUID.fromString(raw.substring(separator + 1));
            return new PostCursor(createdAt, postId);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_MESSAGE);
    }
}
