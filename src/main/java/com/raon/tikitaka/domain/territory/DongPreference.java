package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 희망 동 1·2·3순위. 본진 구의 동만 등록할 수 있고(서비스에서 검증), 대표 자동 배치에 쓴다.
 * 본진이 바뀌면 다른 구의 동이 되므로 본진 변경 적용 시 지운다.
 */
@Entity
@Table(name = "dong_preference", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dong_pref_user_priority", columnNames = {"user_id", "priority"}),
        @UniqueConstraint(name = "uk_dong_pref_user_dong", columnNames = {"user_id", "dong_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DongPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dong_preference_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    /** 1, 2, 3 */
    @Column(name = "priority", nullable = false)
    private int priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dong_id", nullable = false)
    private Dong dong;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DongPreference of(Users user, int priority, Dong dong) {
        if (priority < 1 || priority > 3) {
            throw new IllegalArgumentException("희망 동 순위는 1~3입니다.");
        }
        DongPreference preference = new DongPreference();
        preference.user = user;
        preference.priority = priority;
        preference.dong = dong;
        preference.updatedAt = LocalDateTime.now();
        return preference;
    }
}
