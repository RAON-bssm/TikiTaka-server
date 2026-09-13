package com.raon.tikitaka.adapter.user.dto;

import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;

import java.util.UUID;

/**
 * 내 정보 조회 응답. pendingLocation은 예약이 없으면 null이라 전역 non_null 설정에 의해 생략된다.
 * 값이 있으면 다음 라운드 시작 직후 그 동네로 소속이 옮겨질 예정이라는 뜻이다.
 * userId는 앱이 게시물의 작성자와 대조해 "내 게시물"을 판별하는 데 쓴다.
 */
public record UserInfoResponse(
        UUID userId,
        String userName,
        LocationInfo mainLocation,
        LocationInfo pendingLocation,
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
                LocationInfo.from(user.getPendingLocation()),
                user.getPoint()
        );
    }
}
