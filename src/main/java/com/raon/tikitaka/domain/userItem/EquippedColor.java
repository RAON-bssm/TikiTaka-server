package com.raon.tikitaka.domain.userItem;

import com.raon.tikitaka.domain.color.Color;
import com.raon.tikitaka.domain.enums.ColorGroup;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저가 현재 장착 중인 파츠 그룹별 색상. Equipment(장착 상품)와는 별도로 관리되며,
 * 유저당 그룹(HAIR/EYES)별로 한 행만 존재한다(유니크 제약). equip 시 Equipment와
 * 마찬가지로 delete-then-insert 방식으로 교체한다.
 */
@Entity
@Table(
        name = "equipped_color",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_equipped_color_user_group", columnNames = {"user_id", "color_group"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquippedColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipped_color_id")
    private Long equippedColorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_group", nullable = false)
    private ColorGroup colorGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "color_id", nullable = false)
    private Color color;

    private EquippedColor(Users user, ColorGroup colorGroup, Color color) {
        this.user = user;
        this.colorGroup = colorGroup;
        this.color = color;
    }

    public static EquippedColor of(Users user, ColorGroup colorGroup, Color color) {
        return new EquippedColor(user, colorGroup, color);
    }
}
