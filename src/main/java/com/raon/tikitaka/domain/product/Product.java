package com.raon.tikitaka.domain.product;


import com.raon.tikitaka.domain.enums.ProductType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "product_image")
    private String productImage;

    @Column(name = "product_type")
    private ProductType productType; // 상품 종류 (상의, 하의 등)

    @Column(name = "price")
    private Integer price;

    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean isActive;

}
