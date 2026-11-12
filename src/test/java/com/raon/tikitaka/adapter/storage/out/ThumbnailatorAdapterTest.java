package com.raon.tikitaka.adapter.storage.out;

import com.raon.tikitaka.global.config.ThumbnailProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 썸네일 크기, 형식, 실패 처리. EXIF 회전은 Thumbnailator가 처리하므로(useExifOrientation) 여기서 다시 검증하지 않는다.
 */
class ThumbnailatorAdapterTest {

    private final ThumbnailatorAdapter adapter = adapter(true, 720, 25_000_000L);

    @Test
    void 가로_사진은_가로_720으로_줄이고_비율을_지킨다() throws IOException {
        byte[] thumbnail = adapter.createThumbnail(image(2000, 1500, "jpg", BufferedImage.TYPE_INT_RGB)).orElseThrow();

        BufferedImage read = read(thumbnail);
        assertThat(read.getWidth()).isEqualTo(720);
        assertThat(read.getHeight()).isEqualTo(540);
        assertThat(isJpeg(thumbnail)).isTrue();
    }

    @Test
    void 세로_사진도_가로_720으로_줄인다() throws IOException {
        byte[] thumbnail = adapter.createThumbnail(image(1500, 2000, "jpg", BufferedImage.TYPE_INT_RGB)).orElseThrow();

        BufferedImage read = read(thumbnail);
        assertThat(read.getWidth()).isEqualTo(720);
        assertThat(read.getHeight()).isEqualTo(960);
    }

    @Test
    void 목표보다_작은_사진은_확대하지_않고_JPEG로만_바꾼다() throws IOException {
        byte[] thumbnail = adapter.createThumbnail(image(300, 200, "png", BufferedImage.TYPE_INT_RGB)).orElseThrow();

        BufferedImage read = read(thumbnail);
        assertThat(read.getWidth()).isEqualTo(300);
        assertThat(read.getHeight()).isEqualTo(200);
        assertThat(isJpeg(thumbnail)).isTrue();
    }

    @Test
    void 한_변이라도_목표보다_작으면_줄이지_않는다() throws IOException {
        byte[] thumbnail = adapter.createThumbnail(image(2000, 500, "jpg", BufferedImage.TYPE_INT_RGB)).orElseThrow();

        BufferedImage read = read(thumbnail);
        assertThat(read.getWidth()).isEqualTo(2000);
        assertThat(read.getHeight()).isEqualTo(500);
    }

    @Test
    void 가로_픽셀은_설정값을_따른다() throws IOException {
        ThumbnailatorAdapter small = adapter(true, 400, 25_000_000L);

        byte[] thumbnail = small.createThumbnail(image(2000, 1500, "jpg", BufferedImage.TYPE_INT_RGB)).orElseThrow();

        assertThat(read(thumbnail).getWidth()).isEqualTo(400);
    }

    @Test
    void 투명한_PNG는_흰_배경의_JPEG가_된다() throws IOException {
        // 완전히 투명한 이미지. 배경을 채우지 않으면 JPEG에서 검게 나온다
        byte[] thumbnail = adapter.createThumbnail(image(100, 100, "png", BufferedImage.TYPE_INT_ARGB)).orElseThrow();

        Color center = new Color(read(thumbnail).getRGB(50, 50));
        assertThat(isJpeg(thumbnail)).isTrue();
        // JPEG는 손실 압축이라 정확히 255가 아닐 수 있다
        assertThat(center.getRed()).isGreaterThan(240);
        assertThat(center.getGreen()).isGreaterThan(240);
        assertThat(center.getBlue()).isGreaterThan(240);
    }

    @Test
    void 이미지가_아니면_비어_있다() {
        assertThat(adapter.createThumbnail("hello".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void 자바가_읽지_못하는_형식이면_비어_있다() {
        // HEIC 파일의 앞부분 모양(ftyp 상자). 자바 기본 ImageIO에는 HEIC 리더가 없다
        byte[] heicLike = new byte[64];
        byte[] header = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'};
        System.arraycopy(header, 0, heicLike, 0, header.length);

        assertThat(adapter.createThumbnail(heicLike)).isEmpty();
    }

    @Test
    void 픽셀_수_상한을_넘으면_디코딩하지_않고_비어_있다() throws IOException {
        ThumbnailatorAdapter limited = adapter(true, 720, 10_000L);

        // 200 x 100 = 20,000픽셀 > 10,000
        assertThat(limited.createThumbnail(image(200, 100, "jpg", BufferedImage.TYPE_INT_RGB))).isEmpty();
        // 상한과 같으면 만든다
        assertThat(limited.createThumbnail(image(100, 100, "jpg", BufferedImage.TYPE_INT_RGB))).isPresent();
    }

    @Test
    void 꺼져_있으면_비어_있다() throws IOException {
        ThumbnailatorAdapter disabled = adapter(false, 720, 25_000_000L);

        assertThat(disabled.createThumbnail(image(2000, 1500, "jpg", BufferedImage.TYPE_INT_RGB))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 빈_입력이면_비어_있다(byte[] input) {
        assertThat(adapter.createThumbnail(input)).isEmpty();
    }

    // ===== helpers =====

    private static ThumbnailatorAdapter adapter(boolean enabled, int width, long maxPixels) {
        return new ThumbnailatorAdapter(new ThumbnailProperties(enabled, width, 0.8, maxPixels));
    }

    /** 단색 이미지. ARGB면 아무것도 그리지 않아 완전히 투명하다 */
    private static byte[] image(int width, int height, String format, int type) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        if (type != BufferedImage.TYPE_INT_ARGB) {
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(30, 120, 200));
            graphics.fillRect(0, 0, width, height);
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).isNotNull();
        return image;
    }

    /** JPEG 파일은 FF D8 FF로 시작한다 */
    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }
}
