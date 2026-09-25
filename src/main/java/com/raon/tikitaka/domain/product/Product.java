package com.raon.tikitaka.domain.product;


import com.raon.tikitaka.domain.enums.ProductType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "product")
// 상품 ID는 프론트·백엔드가 공유하는 식별자라 형식을 DB에서 강제한다.
// 케밥케이스만 허용: red-glasses-01 (O) / Red_Glasses (X) / -red- (X)
@Check(name = "ck_product_id_format", constraints = "product_id ~ '^[a-z0-9]+(-[a-z0-9]+)*$'")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    // 자동 증가가 아닌, 상품 등록 시 직접 부여하는 식별자.
    // 한 번 부여하면 변경하지 않는다 (구매·장착 기록과 클라이언트가 이 값을 참조한다).
    @Id
    @Column(name = "product_id", length = 100, nullable = false)
    private String productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_image")
    private String productImage;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type")
    private ProductType productType;

    @Column(name = "price")
    private Integer price;

    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive;

}
