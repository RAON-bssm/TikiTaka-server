package com.raon.tikitaka.domain.user;

import com.raon.tikitaka.domain.enums.UserRole;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.global.exception.InsufficientPointException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "user_name", nullable = false, unique = true)
    private String userName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_location_id")
    private Location mainLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_location_id")
    private Location subLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "point", nullable = false)
    @ColumnDefault("0")
    private Integer point;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void usePoint(int amount) {
        if (this.point < amount) {
            throw new InsufficientPointException();
        }
        this.point -= amount;
    }

}
