package com.raon.tikitaka.adapter.storage.out;

import com.raon.tikitaka.application.storage.out.ThumbnailGeneratorPort;
import com.raon.tikitaka.global.config.ThumbnailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.ImageFilter;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Thumbnailator로 썸네일을 만든다.
 * 1. 이미지 헤더만 읽어 형식과 크기를 확인한다(픽셀을 디코딩하지 않는다). 자바가 읽지 못하는 형식(HEIC, WebP 등)이거나
 *    픽셀 수가 상한을 넘으면 여기서 그만둔다.
 * 2. 디코딩해서 줄이고, EXIF 회전 정보대로 돌려 세운다. 휴대폰 사진은 픽셀을 눕혀 저장하고 EXIF로 방향을 알려 주는데,
 *    새로 쓰는 JPEG에는 EXIF가 없으므로 픽셀 자체를 돌려 두지 않으면 썸네일이 누워서 나온다.
 * 3. 흰 배경 위에 그려 JPEG로 쓴다. JPEG는 투명도가 없어 투명 PNG를 그대로 쓰면 투명한 부분이 검게 나온다.
 *    새로 쓴 파일에는 원본 메타데이터(촬영 위치 GPS 등)가 들어가지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailatorAdapter implements ThumbnailGeneratorPort {

    private static final String OUTPUT_FORMAT = "jpg";

    private static final ImageFilter FLATTEN_ON_WHITE = image -> {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    };

    private final ThumbnailProperties properties;

    @Override
    public Optional<byte[]> createThumbnail(byte[] image) {
        if (!properties.enabled() || image == null || image.length == 0) {
            return Optional.empty();
        }
        try {
            ImageSize size = readSize(image);
            if (size == null) {
                log.info("썸네일 건너뜀: 읽을 수 없는 이미지 형식");
                return Optional.empty();
            }
            if (size.pixels() > properties.maxPixels()) {
                log.info("썸네일 건너뜀: 픽셀 수 상한 초과 width={}, height={}", size.width(), size.height());
                return Optional.empty();
            }
            return Optional.of(resize(image, size));
        } catch (IOException | RuntimeException e) {
            // 메시지에 파일 경로나 내용이 섞일 수 있어 예외 종류만 남긴다
            log.warn("썸네일 생성 실패: error={}", e.getClass().getName());
            return Optional.empty();
        }
    }

    private byte[] resize(byte[] image, ImageSize size) throws IOException {
        var builder = Thumbnails.of(new ByteArrayInputStream(image));
        // EXIF로 90도 돌아가는 사진은 화면에서 가로와 세로가 바뀐다. 두 변이 모두 목표보다 클 때만 줄이면
        // 방향과 상관없이 확대되는 일이 없다
        if (Math.min(size.width(), size.height()) > properties.width()) {
            builder.width(properties.width());
        } else {
            builder.scale(1.0);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // imageType(TYPE_INT_RGB)를 쓰지 않는다. 그러면 줄이는 단계에서 투명한 부분이 먼저 검게 칠해진 뒤 필터가 돈다.
        // 원본 타입 그대로 줄이고, 마지막 필터에서 흰 배경 위에 그려 RGB로 바꾼다
        builder.useExifOrientation(true)
                .addFilter(FLATTEN_ON_WHITE)
                .outputFormat(OUTPUT_FORMAT)
                .outputQuality(properties.quality())
                .toOutputStream(out);
        return out.toByteArray();
    }

    /** 헤더만 읽은 크기. 자바가 읽을 수 있는 형식이 아니면 null이다 */
    private static ImageSize readSize(byte[] image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageSize(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        }
    }

    private record ImageSize(int width, int height) {
        long pixels() {
            return (long) width * height;
        }
    }
}
