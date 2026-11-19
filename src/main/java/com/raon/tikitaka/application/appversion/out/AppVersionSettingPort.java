package com.raon.tikitaka.application.appversion.out;

import com.raon.tikitaka.domain.appversion.AppVersionSetting;

import java.util.List;

public interface AppVersionSettingPort {

    /** app_version 테이블의 전체 행(플랫폼당 한 행, 최대 2행). 행이 없는 플랫폼은 꺼진 것으로 본다 */
    List<AppVersionSetting> findAll();
}
