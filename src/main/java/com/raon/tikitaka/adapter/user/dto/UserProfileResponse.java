package com.raon.tikitaka.adapter.user.dto;

import com.raon.tikitaka.application.ranking.UserRankRow;
import com.raon.tikitaka.application.user.UserProfile;
import com.raon.tikitaka.domain.location.Location;
import com.raon.tikitaka.domain.user.Users;

public record UserProfileResponse(
        String userName,
        String mainLocationCityName,
        String mainLocationName,
        String subLocationCityName,
        String subLocationName,
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
                cityName(user.getSubLocation()),
                locationName(user.getSubLocation()),
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
