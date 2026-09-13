package com.raon.tikitaka.adapter.user.dto;

import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;

import java.util.UUID;

/**
 * 내 정보 조회 응답.
 * userId는 앱이 게시물의 작성자와 대조해 "내 게시물"을 판별하는 데 쓴다.
 */
public record UserInfoResponse(
        UUID userId,
        String userName,
        LocationInfo mainLocation,
        Integer point
) {

    public record LocationInfo(Long locationId,String locationCityName, String locationName) {

        public static LocationInfo from(Location location) {
            if (location == null) {
                return null;
            }
            return new LocationInfo(location.getLocationId(),location.getCityName(),location.getLocationName());
        }
    }

    public static UserInfoResponse from(Users user) {
        return new UserInfoResponse(
                user.getUserId(),
                user.getUserName(),
                LocationInfo.from(user.getMainLocation()),
                user.getPoint()
        );
    }
}
