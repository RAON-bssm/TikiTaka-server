package com.raon.tikitaka.domain.token;

import com.raon.tikitaka.domain.enums.LoginProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Token {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private LoginProvider provider;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRefreshToken(String newRefreshToken) {
        this.refreshToken = newRefreshToken;
    }
}
