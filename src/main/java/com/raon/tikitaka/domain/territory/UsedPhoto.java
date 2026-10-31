package com.raon.tikitaka.domain.territory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** 땅따먹기에 한 번 쓴 사진의 해시. PK 충돌로 재사용을 막는다(범위는 결정 #21) */
@Entity
@Table(name = "used_photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsedPhoto {

    @Id
    @Column(name = "photo_hash", length = 64)
    private String photoHash;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;

    public static UsedPhoto of(String photoHash, UUID userId, LocalDateTime now) {
        UsedPhoto p = new UsedPhoto();
        p.photoHash = photoHash;
        p.userId = userId;
        p.usedAt = now;
        return p;
    }
}
