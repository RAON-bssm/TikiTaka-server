package com.raon.tikitaka.application.user.in;

import com.raon.tikitaka.application.user.UserProfile;

import java.util.UUID;

public interface GetUserProfileUseCase {

    UserProfile getProfile(UUID userId);
}
