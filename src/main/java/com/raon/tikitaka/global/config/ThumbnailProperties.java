package com.raon.tikitaka.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 게시물 목록용 썸네일 설정. application.yaml의 thumbnail 블록과 매핑되고, 값은 환경 변수(THUMBNAIL_*)로 바꾼다.
 *
 * @param enabled   썸네일 생성을 켜고 끈다. 꺼져 있으면 원본만 올리고 목록은 원본 URL을 쓴다(서버 메모리가 부족할 때 끄는 용도)
 * @param width     썸네일 가로 픽셀. 원본의 가로·세로가 모두 이 값보다 클 때만 줄이고, 작은 사진은 확대하지 않는다
 * @param quality   JPEG 품질(0 초과 1 이하). 높을수록 선명하고 파일이 커진다
 * @param maxPixels 썸네일을 만들 원본의 최대 픽셀 수(가로 x 세로). 디코딩하면 픽셀당 약 4바이트를 메모리에 올리므로,
 *                  이보다 큰 사진은 메모리 부족을 피하려고 썸네일 없이 원본만 올린다. 기본 2500만(약 100MB)이다
 */
@ConfigurationProperties(prefix = "thumbnail")
public record ThumbnailProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("720") int width,
        @DefaultValue("0.8") double quality,
        @DefaultValue("25000000") long maxPixels
) {

    public ThumbnailProperties {
        if (width < 1) {
            throw new IllegalArgumentException("thumbnail.width는 1 이상이어야 합니다: " + width);
        }
        if (quality <= 0 || quality > 1) {
            throw new IllegalArgumentException("thumbnail.quality는 0 초과 1 이하여야 합니다: " + quality);
        }
        if (maxPixels < 1) {
            throw new IllegalArgumentException("thumbnail.max-pixels는 1 이상이어야 합니다: " + maxPixels);
        }
    }
}
