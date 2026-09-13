package com.raon.tikitaka.domain.location;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Long locationId;

    @Column(name="city_name",nullable = false)
    private String cityName;

    @Column(name = "location_name", nullable = false)
    private String locationName;

    /**
     * 표시용 전체 지역명. "부산광역시 북구"처럼 도시명과 동네명을 합친다.
     * 팀 이름처럼 지역을 한 덩어리로 보여주는 곳에서 쓴다.
     */
    public String getFullName() {
        return cityName + " " + locationName;
    }

}
