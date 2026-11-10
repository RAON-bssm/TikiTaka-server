package com.raon.tikitaka.application.post;

import java.util.List;

/**
 * 글 목록 한 페이지. nextCursor는 다음 페이지가 있을 때만 값이 있고, 마지막 페이지면 null이다.
 */
public record PostPage(List<PostSummary> posts, String nextCursor) {
}
