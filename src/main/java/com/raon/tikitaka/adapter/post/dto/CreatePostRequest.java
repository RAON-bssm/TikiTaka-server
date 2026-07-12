package com.raon.tikitaka.adapter.post.dto;

import org.springframework.web.multipart.MultipartFile;

public record CreatePostRequest(Long boardId, String content, MultipartFile image) {
}
