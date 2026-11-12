package com.raon.tikitaka.domain.userItem;

import com.raon.tikitaka.domain.product.Product;
import com.raon.tikitaka.domain.user.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "equipment",
        // 착용 정보 조회(GET /api/equipment/{userId}, 글 목록·개인 랭킹의 일괄 조회)와 착용 교체가 user_id로 거른다.
        // user_id는 FK지만 PostgreSQL은 FK에 인덱스를 자동으로 만들지 않아, 없으면 매번 테이블 전체를 읽는다
        indexes = @Index(name = "idx_equipment_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "equipment_id")
    private Long equipmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Equipment(Users user, Product product) {
        this.user = user;
        this.product = product;
    }

    public static Equipment of(Users user, Product product) {
        return new Equipment(user, product);
    }
}
