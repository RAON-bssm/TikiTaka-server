package com.raon.tikitaka.application.appversion.in;

import com.raon.tikitaka.application.appversion.view.AppVersionCheckView;

public interface CheckAppVersionUseCase {

    /**
     * 앱의 플랫폼과 현재 버전으로 업데이트 필요 여부(NONE, OPTIONAL, FORCE)를 판정한다.
     * 플랫폼이 ios, android가 아니거나 버전 형식이 틀리면 400 ResponseStatusException을 던진다.
     */
    AppVersionCheckView check(String platform, String currentVersion);
}
