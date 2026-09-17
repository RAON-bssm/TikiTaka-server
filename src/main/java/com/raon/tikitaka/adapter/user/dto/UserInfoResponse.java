package com.raon.tikitaka.adapter.user.dto;

import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;

import java.util.UUID;

/**
 * 내 정보 조회 응답.
 *
 * mainLocation은 본진이고 지역 점수가 쌓이는 곳이다.
 * currentLocation은 지금 있는 동네이고 이 동네 게시판에만 글을 쓸 수 있다.
 * atHome이 true면 둘이 같다는 뜻이고, 이때 쓴 글만 지역 점수까지 올라간다.
 * pendingLocation은 예약이 없으면 null이라 전역 non_null 설정에 의해 생략된다.
 *
 * userId는 앱이 게시물의 작성자와 대조해 "내 게시물"을 판별하는 데 쓴다.
 */
public record UserInfoResponse(
        UUID userId,
        String userName,
        LocationInfo mainLocation,
        LocationInfo currentLocation,
        LocationInfo pendingLocation,
        boolean atHome,
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
                LocationInfo.from(user.getCurrentLocation()),
                LocationInfo.from(user.getPendingLocation()),
                user.isAtHome(),
                user.getPoint()
        );
    }
}
