package com.raon.tikitaka.domain.territory;

import com.raon.tikitaka.domain.location.Location;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 땅따먹기의 땅 단위인 행정동. 경계(GeoJSON)는 앱 패키지에 있고, 서버는 행정동 코드와 소속 구만 안다.
 * 서버와 앱은 dongCode(통계청 adm_cd2, 10자리) 하나로 같은 동을 가리킨다.
 */
@Entity
@Table(name = "dong", indexes = @Index(name = "idx_dong_location", columnList = "location_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dong_id")
    private Long dongId;

    /** 행정동 코드 adm_cd2. 프론트 열거형 값과 같다. 예: 덕포1동 = 2653060000 */
    @Column(name = "dong_code", nullable = false, unique = true)
    private Long dongCode;

    /** 소속 구. 이 구가 대표 선발·희망 동 범위의 단위다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "dong_name", nullable = false)
    private String dongName;

    /** 경계 데이터 버전. 앱 패키지의 GeoJSON과 같은 값이어야 한다. 예: ver20261001 */
    @Column(name = "source_version", nullable = false)
    private String sourceVersion;
}
