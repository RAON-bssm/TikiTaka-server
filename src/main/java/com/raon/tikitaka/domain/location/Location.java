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
     * 새 지역을 만든다. locationId는 IDENTITY라 저장 시점에 DB가 채운다.
     */
    public static Location of(String locationName) {
        Location location = new Location();
        location.locationName = locationName;
        return location;
    }
}
