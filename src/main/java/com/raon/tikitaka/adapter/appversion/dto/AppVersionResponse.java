package com.raon.tikitaka.adapter.appversion.dto;

import com.raon.tikitaka.application.appversion.UpdateType;
import com.raon.tikitaka.application.appversion.view.AppVersionCheckView;

/**
 * 앱 버전 확인 응답. 키는 snake_case(update_type, current_version, latest_version, min_supported_version, store_url)이고
 * null인 필드는 응답에서 빠진다. store_url은 update_type이 NONE이면 없다.
 */
public record AppVersionResponse(UpdateType updateType, String currentVersion, String latestVersion,
                                 String minSupportedVersion, String storeUrl) {

    public static AppVersionResponse from(AppVersionCheckView view) {
        return new AppVersionResponse(view.updateType(), view.currentVersion(), view.latestVersion(),
                view.minSupportedVersion(), view.storeUrl());
    }
}
