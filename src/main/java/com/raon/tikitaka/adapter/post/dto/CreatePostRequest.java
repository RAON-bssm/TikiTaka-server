package com.raon.tikitaka.adapter.post.dto;

public record CreatePostRequest(Long boardId, String postImage, String content) {
}
