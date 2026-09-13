package com.raon.tikitaka.adapter.user.dto;

import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.application.user.UserProfile;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;

/**
 * 마이페이지 응답. pendingLocation 계열 필드는 변경 예약이 없으면 null이라
 * 전역 non_null 설정에 의해 생략된다.
 */
public record UserProfileResponse(
        String userName,
        String mainLocationCityName,
        String mainLocationName,
        String pendingLocationCityName,
        String pendingLocationName,
        Integer userRank,
        Long userScore,
        Integer point
) {

    public static UserProfileResponse from(UserProfile profile) {
        Users user = profile.user();
        UserRankRow myRanking = profile.myRanking();

        return new UserProfileResponse(
                user.getUserName(),
                cityName(user.getMainLocation()),
                locationName(user.getMainLocation()),
                cityName(user.getPendingLocation()),
                locationName(user.getPendingLocation()),
                myRanking != null ? myRanking.getUserRank() : null,
                myRanking != null ? myRanking.getUserScore() : null,
                user.getPoint()
        );
    }

    private static String cityName(Location location) {
        return location != null ? location.getCityName() : null;
    }

    private static String locationName(Location location) {
        return location != null ? location.getLocationName() : null;
    }
}
