package com.raon.tikitaka.application.auth.in;

import java.util.UUID;

public interface LogoutUseCase {

    void logout(UUID userId);
}
