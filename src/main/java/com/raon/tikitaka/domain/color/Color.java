package com.raon.tikitaka.domain.color;

import com.raon.tikitaka.domain.enums.ColorGroup;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 색상 마스터 테이블. 그룹(HAIR/EYES)별로 실제 착용 가능한 색상 목록을 관리한다.
 * colorCode는 S3 이미지 파일명 조합({productId}-{colorCode})에 그대로 쓰이므로
 * 영문 대문자로 통일한다 (예: RED, BLACK, BROWN).
 */
@Entity
@Table(
        name = "color",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_color_group_code", columnNames = {"color_group", "color_code"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Color {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "color_id")
    private Long colorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_group", nullable = false)
    private ColorGroup colorGroup;

    @Column(name = "color_code", nullable = false, length = 50)
    private String colorCode;

    @Column(name = "color_name", nullable = false, length = 50)
    private String colorName;

    private Color(ColorGroup colorGroup, String colorCode, String colorName) {
        this.colorGroup = colorGroup;
        this.colorCode = colorCode;
        this.colorName = colorName;
    }

    public static Color of(ColorGroup colorGroup, String colorCode, String colorName) {
        return new Color(colorGroup, colorCode, colorName);
    }
}
