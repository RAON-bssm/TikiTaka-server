package com.raon.tikitaka.adapter.user.dto;

import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;

/**
 * 내 정보 조회 응답. subLocation은 설정 전이면 null이라 전역 non_null 설정에 의해 생략된다.
 * pendingLocationSwap이 true면 다음 라운드 시작 직후 메인과 서브가 교환될 예정이다.
 */
public record UserInfoResponse(
        String userName,
        LocationInfo mainLocation,
        LocationInfo subLocation,
        boolean pendingLocationSwap,
        Integer point
) {

    public record LocationInfo(Long locationId, String locationName) {

        public static LocationInfo from(Location location) {
            if (location == null) {
                return null;
            }
            return new LocationInfo(location.getLocationId(), location.getLocationName());
        }
    }

    public static UserInfoResponse from(Users user) {
        return new UserInfoResponse(
                user.getUserName(),
                LocationInfo.from(user.getMainLocation()),
                LocationInfo.from(user.getSubLocation()),
                user.isPendingLocationSwap(),
                user.getPoint()
        );
    }
}
